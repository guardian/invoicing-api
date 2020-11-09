package com.gu.invoicing.preview

import java.time.{DayOfWeek, LocalDate}
import java.time.temporal.TemporalAdjusters.next
import java.time.temporal.ChronoUnit.WEEKS
import com.gu.invoicing.common.ZuoraAuth.{accessToken, zuoraApiHost}
import com.gu.invoicing.preview.Model._
import com.gu.invoicing.common.Http
import com.gu.invoicing.common.DateOps._
import scala.util.chaining._
import pprint._
import scala.annotation.tailrec
import scala.math.BigDecimal.RoundingMode

object Impl {
  def getAccountId(name: String): String = {
    Http(s"$zuoraApiHost/v1/subscriptions/$name")
      .header("Authorization", s"Bearer $accessToken")
      .asString
      .body
      .pipe(read[Subscription](_))
      .accountId
  }

  def getFutureInvoiceItems(accountId: String, startDate: LocalDate): List[InvoiceItem] = {
    Http(s"$zuoraApiHost/v1/operations/billing-preview")
      .header("Authorization", s"Bearer $accessToken")
      .header("Content-Type", "application/json")
      .postData(
        s"""
           |{
           |    "accountId": "$accountId",
           |    "targetDate": "${startDate.plusMonths(13)}",
           |    "assumeRenewal": "Autorenew"
           |}
           |""".stripMargin
      )
      .method("POST")
      .asString
      .body
      .pipe(read[BillingPreview](_))
      .invoiceItems
  }

  def getPastInvoiceItems(
    account: String,
    subscriptionName: String,
    startDate: LocalDate
  ): List[InvoiceItem] =
    Http(s"$zuoraApiHost/v1/transactions/invoices/accounts/$account")
      .header("Authorization", s"Bearer ${accessToken}")
      .asString
      .body
      .pipe(read[Invoices](_))
      .invoices
      .iterator
      .filter  { _.amount >= 0.0                        }
      .filter  { _.status == "Posted"                   }
      .flatMap { _.invoiceItems                         }
      .filter  { _.subscriptionName == subscriptionName }
      .filter  { item => startDate.inClosedInterval(item.serviceStartDate, item.serviceEndDate) }
      .toList

  def collectRelevantInvoiceItems(
    subscriptionName: String,
    invoiceItems: List[InvoiceItem]
  ): List[InvoiceItem] =
    invoiceItems
      .iterator
      .filter(_.subscriptionName == subscriptionName)
      .filterNot(_.productName == "Discounts")
      .filterNot(isDigitalProduct)
      .filterNot(_.chargeAmount < 0.0)
      .filterNot(v => v.serviceStartDate == v.serviceEndDate)
      .toList
      .sortBy(_.serviceStartDate)

  /** Some products are outright digital (Contribution) and some have a digital component (paper + digital) */
  def isDigitalProduct(item: InvoiceItem): Boolean = {
    List(
      "digi",
      "contrib",
      "support",
    ).exists(item.chargeName.toLowerCase.contains)
  }

  def findNextInvoiceDate(
    items: List[InvoiceItem],
    today: LocalDate = LocalDate.now()
  ): Option[LocalDate] =
    items
      .filter(_.serviceStartDate.isAfter(today))
      .sortBy(_.serviceStartDate)
      .headOption
      .map(_.serviceStartDate)

  def findAffectedPublicationsWithRange(
    publications: List[Publication],
    start: LocalDate,
    end: LocalDate,
  ): List[Publication] = {
    publications
      .filter(_.publicationDate.inClosedInterval(start, end))
      .sortBy(_.publicationDate)
      .distinct
  }

  def chargeNameToDay(item: InvoiceItem): DayOfWeek = {
    item.chargeName match {
      case _ if isDigitalProduct(item) => throw new RuntimeException(s"Non physical paper products should not be handled: $item")
      case "Monday" => DayOfWeek.MONDAY
      case "Tuesday" => DayOfWeek.TUESDAY
      case "Wednesday" => DayOfWeek.WEDNESDAY
      case "Thursday" => DayOfWeek.THURSDAY
      case "Friday" => DayOfWeek.FRIDAY
      case "Saturday" => DayOfWeek.SATURDAY
      case "Sunday" => DayOfWeek.SUNDAY
      case _ => DayOfWeek.FRIDAY // Guardian Weekly
    }
  }

  /**
   * Publication is of higher granularity than InvoiceItem. The InvoiceItem is a Zuora model representing a charge
   * that spans a service period, that is, a billing period, whilst Publication is a Guardian model representing
   * a single publication on a particular day in a week. For example, a single InvoiceItem of a Sunday Home Delivery
   * with service period spanning one month actually represents four separate Sunday publications.
   */
  def splitInvoiceItemIntoPublications(
    invoiceItem: InvoiceItem,
  ): List[Publication] = {

    @tailrec def loop(
      currentStart: LocalDate,
      endDateInclusive: LocalDate,
      day: DayOfWeek,
      publications: List[Publication]
    ): List[Publication] = {
      if (currentStart.isAfter(endDateInclusive)) {
        publications
      } else {
        loop(
          currentStart `with` next(day),
          endDateInclusive,
          day,
          Publication(
            publicationDate = currentStart,
            invoiceDate = invoiceItem.serviceStartDate,
            nextInvoiceDate = invoiceItem.serviceEndDate.plusDays(1),
            invoiceItem.productName,
            invoiceItem.chargeName,
            chargeNameToDay(invoiceItem),
            pricePerPublication(invoiceItem),
          ) :: publications
        )
      }
    }

    loop(
      invoiceItem.serviceStartDate,
      invoiceItem.serviceEndDate,
      chargeNameToDay(invoiceItem),
      Nil,
    )
  }

  /**
   * Predict approximate price per publication by determining number of weeks in a service period and
   * then dividing by invoice item charge amount.
   */
  def pricePerPublication(invoiceItem: InvoiceItem): Double = {
    def round2Places(d: Double): Double = BigDecimal(d).setScale(2, RoundingMode.UP).toDouble
    def roundedWeeks(weeks: Long): Long = weeks match {
      case 12 | 13 => 13 // Quarter
      case 51 | 52 => 52 // Annual
      case 25 | 26 => 26 // Semi_annual
      case  3 |  4 => 4  // Month
      case  5 |  6 => 6  // 6 for 6
      case  v => log(invoiceItem, s"WARN: Check publication price for unusual billing period of $weeks"); v
    }

    val approxBillingPeriodInWeeks = roundedWeeks(
      WEEKS.between( // plus one because between is exclusive on right bound but serviceEndDate is inclusive
        invoiceItem.serviceStartDate,
        invoiceItem.serviceEndDate.plusDays(1)
      )
    )
    round2Places(invoiceItem.chargeAmount / approxBillingPeriodInWeeks)
  }
}
