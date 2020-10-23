package com.gu.invoicing.nextinvoicedate

import java.time.LocalDate
import com.gu.invoicing.common.ZuoraAuth.{accessToken, zuoraApiHost}
import com.gu.invoicing.nextinvoicedate.Model._
import com.gu.invoicing.common.Http
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
}
