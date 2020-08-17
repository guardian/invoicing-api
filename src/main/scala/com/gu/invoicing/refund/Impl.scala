package com.gu.invoicing.refund

import com.gu.invoicing.common.ZuoraAuth.{accessToken, zuoraApiHost}
import java.time.LocalDate
import scalaj.http.{BaseHttp, HttpOptions}
import scala.annotation.tailrec
import Model._
import scala.util.chaining._

/**
 * Zuora API client and implementation details
 */
object Impl {
  object HttpWithLongTimeout extends BaseHttp(
    options = Seq(
      HttpOptions.connTimeout(5000),
      HttpOptions.readTimeout(5 * 60 * 1000),
      HttpOptions.followRedirects(false)
    )
  )

  def getSubscription(name: String): Subscription = {
    HttpWithLongTimeout(s"$zuoraApiHost/v1/subscriptions/$name")
      .header("Authorization", s"Bearer $accessToken")
      .asString
      .body
      .pipe(read[Subscription](_))
  }

  def getAccountBalance(accountId: String): BigDecimal = {
    HttpWithLongTimeout(s"$zuoraApiHost/v1/accounts/$accountId")
      .header("Authorization", s"Bearer $accessToken")
      .asString
      .body
      .pipe(read[Account](_))
      .metrics
      .balance
  }

  def getInvoices(accountId: String): List[Invoice] = {
    HttpWithLongTimeout(s"$zuoraApiHost/v1/action/query")
      .header("Authorization", s"Bearer $accessToken")
      .header("Content-Type", "application/json")
      .postData(s"""{"queryString": "select Id, Amount, Balance, InvoiceDate, InvoiceNumber, PaymentAmount, TargetDate, Status from Invoice where AccountId = '$accountId'"}""")
      .method("POST")
      .asString
      .body
      .pipe(read[InvoiceQueryResult](_))
      .records
  }

  def getInvoiceItems(invoiceId: String): List[InvoiceItem] = {
    HttpWithLongTimeout(s"$zuoraApiHost/v1/invoices/$invoiceId/items")
      .header("Authorization", s"Bearer $accessToken")
      .asString
      .body
      .pipe(read[InvoiceItems](_))
      .invoiceItems
  }

  def getItemsByInvoice(subscriptionName: String): Map[String, List[InvoiceItem]] = {
    HttpWithLongTimeout(s"$zuoraApiHost/v1/action/query")
      .header("Authorization", s"Bearer $accessToken")
      .header("Content-Type", "application/json")
      .postData(s"""{"queryString": "select Id, ChargeAmount, ChargeDate, ChargeName, ChargeNumber, InvoiceId, ProductName, ServiceEndDate, ServiceStartDate, SubscriptionNumber FROM InvoiceItem where SubscriptionNumber = '$subscriptionName'"}""".stripMargin)
      .method("POST")
      .asString
      .body
      .pipe(read[InvoiceItemQueryResult](_))
      .records
      .groupBy(_.InvoiceId)
  }

  def getInvoicePaymentId(invoiceId: String): Option[String] = {
    HttpWithLongTimeout(s"$zuoraApiHost/v1/action/query")
      .header("Authorization", s"Bearer $accessToken}")
      .header("Content-Type", "application/json")
      .postData(s"""{"queryString": "select Id, invoiceId, paymentId from InvoicePayment where invoiceId = '$invoiceId'"}""")
      .method("POST")
      .asString
      .body
      .pipe(read[InvoicePaymentQueryResult](_))
      .records
      .headOption
      .map(_.PaymentId)
  }

  def createRefundObject(amount: BigDecimal, paymentId: String, comment: String): String = {
    HttpWithLongTimeout(s"$zuoraApiHost/v1/object/refund")
      .header("Authorization", s"Bearer $accessToken")
      .header("Content-Type", "application/json")
      .postData(
        s"""
           |{
           |  "Amount": $amount,
           |  "Comment": "$comment",
           |  "PaymentId": "$paymentId",
           |  "Type": "Electronic"
           |}
           |""".stripMargin)
      .method("POST")
      .asString
      .body
      .pipe(body => read[RefundResult](body))
      .Id
  }

  def getRefundStatus(refundId: String): String = {
    HttpWithLongTimeout(s"$zuoraApiHost/v1/object/refund/$refundId")
      .header("Authorization", s"Bearer $accessToken")
      .asString
      .body
      .pipe(read[Refund](_))
      .Status
  }

  def netAdjustmentsByInvoiceItemId(adjustments: List[InvoiceItemAdjustment]): Map[String, BigDecimal] = {
    adjustments
      .groupBy(_.SourceId)
      .map { case (invoiceItemId, adjustments) =>
        val netCredits = adjustments.filter(_.Type == "Credit").map(_.Amount).sum
        val netCharges = adjustments.filter(_.Type == "Charge").map(_.Amount).sum
        (invoiceItemId, netCredits - netCharges)
      }
  }

  /**
   * This is likely the most complicated part of the program. It decides which invoice items to adjust and
   * by how much by taking into account any previous adjustments already made to corresponding items.
   */
  def spreadRefundAcrossItems(
    invoiceItems: List[InvoiceItem],
    adjustments: List[InvoiceItemAdjustment],
    totalRefundAmount: BigDecimal,
    refundGuid: String,
  ): List[InvoiceItemAdjustmentWrite] = {

    /* Collect all item adjustments of a particular invoice item and return remaining amount that can be adjusted/refunded */
    def availableAmount(invoiceItem: InvoiceItem): Option[BigDecimal] = {
      netAdjustmentsByInvoiceItemId(adjustments).get(invoiceItem.Id) match {
        case Some(netAdjustment) =>
          val availableRefundableAmount = invoiceItem.ChargeAmount - netAdjustment
          if (availableRefundableAmount <= 0) None else Some(availableRefundableAmount)

        case None => // this items has not been adjusted therefore the original full item amount is available
          Some(invoiceItem.ChargeAmount)
      }
    }

    @tailrec def loop(remainingAmounToRefund: BigDecimal, remainingItems: List[InvoiceItem], accumulatedAdjustments: List[InvoiceItemAdjustmentWrite]): List[InvoiceItemAdjustmentWrite] = {
      remainingItems match {
        case Nil =>
          accumulatedAdjustments

        case nextItem :: tail =>
          val adjustItemBy: BigDecimal => InvoiceItemAdjustmentWrite =
            InvoiceItemAdjustmentWrite(LocalDate.now(), _, refundGuid, nextItem.InvoiceId, "Credit", "InvoiceDetail", nextItem.Id)

          availableAmount(nextItem) match {
            case Some(availableRefundableAmount) =>
              if ((remainingAmounToRefund - availableRefundableAmount) <= 0)
                adjustItemBy(remainingAmounToRefund) :: accumulatedAdjustments
              else
                loop(remainingAmounToRefund - availableRefundableAmount, tail, adjustItemBy(availableRefundableAmount) :: accumulatedAdjustments)

            case None =>
              loop(remainingAmounToRefund, tail, accumulatedAdjustments)
          }

      }
    }

    loop(totalRefundAmount, invoiceItems, List.empty[InvoiceItemAdjustmentWrite])
  }

  def applyRefundOverItemAdjustments(invoiceItems: List[InvoiceItemAdjustmentWrite]): List[AdjustmentResult] = {
    HttpWithLongTimeout(s"$zuoraApiHost/v1/action/create")
      .header("Authorization", s"Bearer $accessToken")
      .header("Content-Type", "application/json")
      .postData(write(InvoiceItemAdjustmentsWriteRequest(objects = invoiceItems, `type` = "InvoiceItemAdjustment")))
      .method("POST")
      .asString
      .body
      .pipe(read[List[AdjustmentResult]](_))
  }

  def joinInvoiceWithInvoiceItemsOnInvoiceIdKey(
    invoices: List[Invoice],
    itemsByInvoiceId: Map[String, List[InvoiceItem]]
  ): List[(String, Invoice, List[InvoiceItem])] = {
    invoices.map(invoice => (invoice.Id, invoice, itemsByInvoiceId(invoice.Id)))
  }

  /** Select correct invoice to apply refund to */
  def decideRelevantInvoice(invoices: List[Invoice], itemsByInvoiceId: Map[String, List[InvoiceItem]]): (String, Invoice, List[InvoiceItem]) = {
    joinInvoiceWithInvoiceItemsOnInvoiceIdKey(invoices, itemsByInvoiceId)
      .iterator
      .filter({ case (invoiceId, invoice, invoiceItems) => invoice.Status == "Posted"})
      .filter({ case (invoiceId, invoice, invoiceItems) => invoice.Amount > 0})
      .maxBy({ case (invoiceId, invoice, invoiceItems) => invoice.TargetDate })
  }

  def getInvoiceItemAdjustments(invoiceId: String): List[InvoiceItemAdjustment] = {
    HttpWithLongTimeout(s"$zuoraApiHost/v1/action/query")
      .header("Authorization", s"Bearer $accessToken")
      .header("Content-Type", "application/json")
      .postData(s"""{"queryString": "select Id, InvoiceId, InvoiceItemName, SourceId, SourceType, Status, Type, Amount FROM InvoiceItemAdjustment where InvoiceId = '$invoiceId'"}""")
      .method("POST")
      .asString
      .body
      .pipe(read[InvoiceItemAdjustmentsQueryResult](_))
      .records
  }

  /**
   * Zuora uses Half Up rounding to two decimal places with rounding increment of 0.1
   * Corresponds to rounding rules specified under Zuora | Billing Settings | Customize Currencies
   */
  def roundHalfUp(x: BigDecimal): BigDecimal = x.setScale(2, BigDecimal.RoundingMode.HALF_UP)

  // https://knowledgecenter.zuora.com/Billing/Billing_and_Payments/TB_Rounding_and_Precision
  def roundAdjustments(adjustments: List[InvoiceItemAdjustmentWrite]): List[InvoiceItemAdjustmentWrite] = {
    adjustments
      .filter(a => roundHalfUp(a.Amount) != 0)
      .map(a => a.copy(Amount = roundHalfUp(a.Amount)))
  }

}
