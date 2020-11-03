package com.gu.invoicing.preview

import java.time.{DayOfWeek, LocalDate}
import java.time.temporal.{ChronoUnit, TemporalAdjusters}
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
      .filter  { _.serviceStartDate >= startDate        }
      .toList

  def collectRelevantInvoiceItems(
    subscriptionName: String,
    invoiceItems: List[InvoiceItem]
  ): List[InvoiceItem] =
    invoiceItems
      .iterator
      .filter(_.subscriptionName == subscriptionName)
      .filterNot(_.productName == "Discounts")
      .filterNot(_.chargeAmount < 0.0)
      .filterNot(v => v.serviceStartDate == v.serviceEndDate)
      .toList
      .sortBy(_.serviceStartDate)

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

  def invoiceItemToPublication(item: InvoiceItem): Publication =
    Publication(
      item.serviceStartDate,
      item.serviceEndDate,
      item.serviceEndDate.plusDays(1),
      item.productName,
      item.chargeName,
      chargeNameToDay(item.chargeName),
      pricePerPublication(item),
    )

  def chargeNameToDay(name: String): DayOfWeek = {
    name match {
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
          currentStart.`with`(TemporalAdjusters.next(day)),
          endDateInclusive,
          day,
          Publication(
            publicationDate = currentStart,
            invoiceDate = invoiceItem.serviceStartDate,
            nextInvoiceDate = invoiceItem.serviceEndDate.plusDays(1),
            invoiceItem.productName,
            invoiceItem.chargeName,
            chargeNameToDay(invoiceItem.chargeName),
            pricePerPublication(invoiceItem),
          ) :: publications
        )
      }
    }

    loop(
      invoiceItem.serviceStartDate,
      invoiceItem.serviceEndDate,
      chargeNameToDay(invoiceItem.chargeName),
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
      case  v =>
        warn(s"Double check price per publication for $invoiceItem due to unusual billing period of $weeks weeks")
        v
    }
    val approxBillingPeriodInWeeks =
      roundedWeeks(WEEKS.between(invoiceItem.serviceStartDate, invoiceItem.serviceEndDate.plusDays(1)))

    round2Places(invoiceItem.chargeAmount / approxBillingPeriodInWeeks)
  }

  /**
   * Unfold publications falling within current invoice period from publications at the head
   * of the next invoiced period by rewinding back publication date few weeks.
   *
   * For example, given 2020-10-28 Wednesday, it will generate 4 previous Wednesday publications
   * on 21st, 14th, etc.
   *
   * This is needed because billing-preview does not generate current invoice period, but there could be
   * holiday stops requests still within current invoiced period. For such stops which are scheduled for
   * before next invoiced period, we know that they will always be credited in the next invoice period,
   * so we do not have to adjust nextInvoiceDate, and instead we just copy and mutate publicationDate.
   *
   * Note this method is a sister method to splitInvoiceItemIntoPublications which handles calculation
   * of publicationDates for issues starting next invoice date (which uses Zuora billing-preview).
   */
  @deprecated("In favour of getPastInvoiceItems") def rewind(publications: List[Publication]): List[Publication] = {
    @tailrec def loop(n: Int, pubs: List[Publication]): List[Publication] = {
      pubs match {
        case pub :: t if n > 0 => loop(n - 1, pub.previous :: pubs)
        case _ => pubs
      }
    }

    DayOfWeek.values.toList.flatMap { day =>
      publications
        .sortBy(_.publicationDate)
        .collectFirst { case pub if pub.dayOfWeek == day => loop(4, List(pub)) }
    }.flatten ++ publications
  }
}
