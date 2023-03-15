package com.gu.invoicing.refund

import com.gu.invoicing.common.ZuoraAuth.{accessToken, zuoraApiHost}
import java.time.LocalDate
import com.gu.invoicing.common.Http
import scala.annotation.tailrec
import Model._
import scala.util.chaining._

/** Zuora API client and implementation details
  */
object Impl {
  def getSubscription(name: String): Subscription =
    get[Subscription](s"$zuoraApiHost/v1/subscriptions/$name")

  def getAccountBalance(accountId: String): BigDecimal = {
    get[Account](s"$zuoraApiHost/v1/accounts/$accountId")
      .metrics
      .balance
  }

  def getInvoices(accountId: String): List[Invoice] =
    post[InvoiceQueryResult](
      s"$zuoraApiHost/v1/action/query",
      s"""{"queryString": "select Id, Amount, Balance, InvoiceDate, InvoiceNumber, PaymentAmount, TargetDate, Status from Invoice where AccountId = '$accountId'"}"""
    )
      .records

  def getInvoiceItems(invoiceId: String): List[InvoiceItem] =
    get[InvoiceItems](s"$zuoraApiHost/v1/invoices/$invoiceId/items")
      .invoiceItems

  def getItemsByInvoice(subscriptionName: String): Map[String, List[InvoiceItem]] =
    post[InvoiceItemQueryResult](
      s"$zuoraApiHost/v1/action/query",
      s"""{"queryString": "select Id, ChargeAmount, ChargeDate, ChargeName, ChargeNumber, InvoiceId, ProductName, ServiceEndDate, ServiceStartDate, SubscriptionNumber, UnitPrice FROM InvoiceItem where SubscriptionNumber = '$subscriptionName'"}""".stripMargin
    )
      .records
      .groupBy(_.InvoiceId)


  def getInvoicePaymentId(invoiceId: String): Option[String] =
    post[InvoicePaymentQueryResult](
      s"$zuoraApiHost/v1/action/query",
      s"""{"queryString": "select Id, invoiceId, paymentId, CreatedDate from InvoicePayment where invoiceId = '$invoiceId'"}"""
    )
      .records
      .sortBy(_.CreatedDate)
      .reverse
      .headOption
      .map(_.PaymentId)

  def createRefundObject(amount: BigDecimal, paymentId: String, comment: String): String =
    post[RefundResult](s"$zuoraApiHost/v1/object/refund",
      s"""
         |{
         |  "Amount": $amount,
         |  "Comment": "$comment",
         |  "PaymentId": "$paymentId",
         |  "Type": "Electronic"
         |}
         |""".stripMargin)
      .Id

  def getRefundStatus(refundId: String): String =
    get[Refund](s"$zuoraApiHost/v1/object/refund/$refundId")
      .Status

  def netAdjustmentsByInvoiceItemId(
      adjustments: List[InvoiceItemAdjustment]
  ): Map[String, BigDecimal] = {
    adjustments
      .groupBy(_.SourceId)
      .map { case (invoiceItemId, adjustments) =>
        val netCredits = adjustments.filter(_.Type == "Credit").map(_.Amount).sum
        val netCharges = adjustments.filter(_.Type == "Charge").map(_.Amount).sum
        (invoiceItemId, netCredits - netCharges)
      }
  }

  /** This is likely the most complicated part of the program. It decides which invoice items to
    * adjust and by how much by taking into account any previous adjustments already made to
    * corresponding items.
    */
  def spreadRefundAcrossItems(
      invoiceItems: List[InvoiceItem],
      adjustments: List[InvoiceItemAdjustment],
      totalRefundAmount: BigDecimal,
      refundGuid: String
  ): List[InvoiceItemAdjustmentWrite] = {

    /* Collect all item adjustments of a particular invoice item and return remaining amount that can be adjusted/refunded */
    def availableAmount(invoiceItem: InvoiceItem): Option[BigDecimal] = {
      netAdjustmentsByInvoiceItemId(adjustments).get(invoiceItem.Id) match {
        case Some(netAdjustment) =>
          val availableRefundableAmount = invoiceItem.UnitPrice - netAdjustment
          if (availableRefundableAmount <= 0) None else Some(availableRefundableAmount)

        case None => // this items has not been adjusted therefore the original full item amount is available
          Some(
            invoiceItem.UnitPrice
          ) // Use unit price rather than charge amount here because charge amount does not include tax
      }
    }

    @tailrec def loop(
        remainingAmounToRefund: BigDecimal,
        remainingItems: List[InvoiceItem],
        accumulatedAdjustments: List[InvoiceItemAdjustmentWrite]
    ): List[InvoiceItemAdjustmentWrite] = {
      remainingItems match {
        case Nil =>
          accumulatedAdjustments

        case nextItem :: tail =>
          val adjustItemBy: BigDecimal => InvoiceItemAdjustmentWrite =
            InvoiceItemAdjustmentWrite(
              LocalDate.now(),
              _,
              refundGuid,
              nextItem.InvoiceId,
              "Credit",
              "InvoiceDetail",
              nextItem.Id
            )

          availableAmount(nextItem) match {
            case Some(availableRefundableAmount) =>
              if ((remainingAmounToRefund - availableRefundableAmount) <= 0)
                adjustItemBy(remainingAmounToRefund) :: accumulatedAdjustments
              else
                loop(
                  remainingAmounToRefund - availableRefundableAmount,
                  tail,
                  adjustItemBy(availableRefundableAmount) :: accumulatedAdjustments
                )

            case None =>
              loop(remainingAmounToRefund, tail, accumulatedAdjustments)
          }

      }
    }

    loop(totalRefundAmount, invoiceItems, List.empty[InvoiceItemAdjustmentWrite])
  }

  def applyRefundOverItemAdjustments(invoiceItems: List[InvoiceItemAdjustmentWrite]): List[AdjustmentResult] =
    post[List[AdjustmentResult]](s"$zuoraApiHost/v1/action/create",
      write(
        InvoiceItemAdjustmentsWriteRequest(
          objects = invoiceItems,
          `type` = "InvoiceItemAdjustment"
        )
      )
    )

  def joinInvoiceWithInvoiceItemsOnInvoiceIdKey(
      invoices: List[Invoice],
      itemsByInvoiceId: Map[String, List[InvoiceItem]]
  ): List[(String, Invoice, List[InvoiceItem])] = {
    invoices.map(invoice => (invoice.Id, invoice, itemsByInvoiceId.get(invoice.Id))) collect {
      case (invoiceId, invoice, Some(invoiceItems)) => (invoiceId, invoice, invoiceItems)
    }
  }

  /** Select correct invoice to apply refund to */
  def decideRelevantInvoice(
      invoices: List[Invoice],
      itemsByInvoiceId: Map[String, List[InvoiceItem]]
  ): (String, Invoice, List[InvoiceItem]) = {
    joinInvoiceWithInvoiceItemsOnInvoiceIdKey(invoices, itemsByInvoiceId).iterator
      .filter({ case (_, invoice, _) => invoice.Status == "Posted" })
      .filter({ case (_, invoice, _) => invoice.Amount > 0 })
      .maxBy({ case (_, invoice, _) => invoice.TargetDate })
  }

  def getInvoiceItemAdjustments(invoiceId: String): List[InvoiceItemAdjustment] =
    post[InvoiceItemAdjustmentsQueryResult](
      s"$zuoraApiHost/v1/action/query",
      s"""{"queryString": "select Id, InvoiceId, InvoiceItemName, SourceId, SourceType, Status, Type, Amount FROM InvoiceItemAdjustment where InvoiceId = '$invoiceId'"}"""
    )
      .records

  /** Zuora uses Half Up rounding to two decimal places with rounding increment of 0.1 Corresponds
    * to rounding rules specified under Zuora | Billing Settings | Customize Currencies
    */
  def roundHalfUp(x: BigDecimal): BigDecimal = x.setScale(2, BigDecimal.RoundingMode.HALF_UP)

  // https://knowledgecenter.zuora.com/Billing/Billing_and_Payments/TB_Rounding_and_Precision
  def roundAdjustments(
      adjustments: List[InvoiceItemAdjustmentWrite]
  ): List[InvoiceItemAdjustmentWrite] = {
    adjustments
      .filter(a => roundHalfUp(a.Amount) != 0)
      .map(a => a.copy(Amount = roundHalfUp(a.Amount)))
  }

  def logAndRead[T: Reader](url: String, response: String): T = {
    System.out.println(s"Received ${stripNewlines(response)} from $url")
    read[T](response)
  }

  def get[T: Reader](url: String): T = {
    System.out.println(s"Calling GET $url")
    Http(url)
      .header("Authorization", s"Bearer $accessToken")
      .asString
      .body
      .pipe(logAndRead[T](url, _))
  }

  def post[T: Reader](url: String, body: String): T = {
    System.out.println(s"Calling POST $url with body ${stripNewlines(body)}")
    Http(url)
      .header("Authorization", s"Bearer $accessToken")
      .header("Content-Type", "application/json")
      .postData(body)
      .method("POST")
      .asString
      .body
      .pipe(logAndRead[T](url, _))
  }

  private def stripNewlines(body: String) =
    body.replace("\n", "")

}
