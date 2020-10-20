package com.gu.invoicing.nextinvoicedate

import java.time.LocalDate
import com.gu.invoicing.common.ZuoraAuth.{accessToken, zuoraApiHost}
import com.gu.invoicing.nextinvoicedate.Model._
import scalaj.http.Http
import scala.util.chaining._
import pprint._

object Impl {
  def getAccountId(name: String): String = {
    Http(s"$zuoraApiHost/v1/subscriptions/$name")
      .header("Authorization", s"Bearer $accessToken")
      .asString
      .body
      .pipe(read[Subscription](_))
      .accountId
  }
  def getBillingPreview(accountId: String): List[InvoiceItem] = {
    Http(s"$zuoraApiHost/v1/operations/billing-preview")
      .header("Authorization", s"Bearer $accessToken")
      .header("Content-Type", "application/json")
      .postData(
        s"""
          |{
          |    "accountId": "$accountId",
          |    "targetDate": "${LocalDate.now.plusMonths(13)}",
          |    "assumeRenewal": "All"
          |}
          |""".stripMargin
      )
      .method("POST")
      .asString
      .body
      .pipe(read[BillingPreview](_))
      .invoiceItems
  }

  def collectRelevantInvoiceItems(
    subscriptionName: String,
    invoiceItems: List[InvoiceItem]
  ): List[InvoiceItem] = {
    invoiceItems
      .iterator
      .filter(_.subscriptionName == subscriptionName)
      .filterNot(_.productName == "Discounts")
      .filterNot(_.chargeAmount < 0.0)
      .filterNot(v => v.serviceStartDate == v.serviceEndDate)
      .toList
      .sortBy(_.serviceStartDate)
  }

  def findNextInvoiceDate(
    items: List[InvoiceItem],
    today: LocalDate = LocalDate.now()
  ): Option[LocalDate] = {
    items
      .sliding(2, 2)
      .collectFirst { case List(current, next) if isActiveInvoicedPeriod(current, today) => next }
      .map(_.serviceStartDate)
  }

  private def isActiveInvoicedPeriod(item: InvoiceItem, today: LocalDate): Boolean =
    today.isBefore(item.serviceEndDate) || today.isEqual(item.serviceEndDate)
}
