package com.gu.invoicing.refund

import com.gu.invoicing.common.ZuoraAuth.{accessToken, zuoraApiHost}

import java.time.LocalDate
import com.gu.invoicing.common.Http

import scala.annotation.tailrec
import Model._
import com.gu.invoicing.common.Assert.StringAssert

import scala.util.chaining._

/** Zuora API client and implementation details
  */
object Impl {
  def getSubscription(name: String): Subscription =
    get[Subscription](s"$zuoraApiHost/v1/subscriptions/$name")

  def getAccountBalance(accountId: String): BigDecimal = {
    get[Account](s"$zuoraApiHost/v1/accounts/$accountId").metrics.balance
  }

  def getInvoices(accountId: String): List[Invoice] =
    post[InvoiceQueryResult](
      s"$zuoraApiHost/v1/action/query",
      s"""{"queryString": "select Id, Amount, Balance, InvoiceDate, InvoiceNumber, PaymentAmount, TargetDate, Status from Invoice where AccountId = '$accountId'"}""",
    ).records

  def getInvoiceItems(invoiceId: String): List[InvoiceItem] =
    get[InvoiceItems](s"$zuoraApiHost/v1/invoices/$invoiceId/items").invoiceItems

  def getItemsByInvoice(subscriptionName: String): Map[String, List[InvoiceItem]] =
    post[InvoiceItemQueryResult](
      s"$zuoraApiHost/v1/action/query",
      s"""{"queryString": "select Id, ChargeAmount, TaxAmount, ChargeDate, ChargeName, ChargeNumber, InvoiceId, ProductName, ServiceEndDate, ServiceStartDate, SubscriptionNumber FROM InvoiceItem where SubscriptionNumber = '$subscriptionName'"}""".stripMargin,
    ).records
      .groupBy(_.InvoiceId)

  def getTaxationItemsForInvoice(invoiceId: String): List[TaxationItem] =
    post[TaxationItemQueryResult](
      s"$zuoraApiHost/v1/action/query",
      s"""{"queryString": "select Id, InvoiceId, InvoiceItemId FROM TaxationItem where InvoiceId = '$invoiceId'"}""".stripMargin,
    ).records

  def getInvoicePaymentId(invoiceId: String): Option[String] =
    post[InvoicePaymentQueryResult](
      s"$zuoraApiHost/v1/action/query",
      s"""{"queryString": "select Id, invoiceId, paymentId, CreatedDate from InvoicePayment where invoiceId = '$invoiceId'"}""",
    ).records
      .sortBy(_.CreatedDate)
      .reverse
      .headOption
      .map(_.PaymentId)

  def createRefundObject(amount: BigDecimal, paymentId: String, comment: String): String =
    post[RefundResult](
      s"$zuoraApiHost/v1/object/refund",
      s"""
         |{
         |  "Amount": $amount,
         |  "Comment": "$comment",
         |  "PaymentId": "$paymentId",
         |  "Type": "Electronic"
         |}
         |""".stripMargin,
    ).Id

  def getRefundStatus(refundId: String): String =
    get[Refund](s"$zuoraApiHost/v1/object/refund/$refundId").Status

  def netAdjustmentsByInvoiceItemId(
      adjustments: List[InvoiceItemAdjustment],
  ): Map[String, BigDecimal] = {
    adjustments
      .groupBy(_.SourceId)
      .map { case (invoiceItemId, adjustments) =>
        val netCredits = adjustments.filter(_.Type == "Credit").map(_.Amount).sum
        val netCharges = adjustments.filter(_.Type == "Charge").map(_.Amount).sum
        (invoiceItemId, netCredits - netCharges)
      }
  }

  def invoiceHasTaxationItems(invoiceItems: List[InvoiceItem]): Boolean = {
    invoiceItems.exists(_.TaxAmount > 0)
  }

  /* Collect all item adjustments of a particular invoice item and return remaining amount that can be adjusted/refunded */
  def availableAmount(invoiceItem: InvoiceItem, adjustments: List[InvoiceItemAdjustment]): Option[BigDecimal] = {
    netAdjustmentsByInvoiceItemId(adjustments).get(invoiceItem.Id) match {
      case Some(netAdjustment) =>
        val availableRefundableAmount = invoiceItem.amountWithTax - netAdjustment
        if (availableRefundableAmount <= 0) None else Some(availableRefundableAmount)

      case None => // this items has not been adjusted therefore the original full item amount is available
        if (invoiceItem.amountWithTax <= 0) None else Some(invoiceItem.amountWithTax)
    }
  }

  /** This is likely the most complicated part of the program. It decides which invoice items to adjust and by how much
    * by taking into account any previous adjustments already made to corresponding items.
    */
  def spreadRefundAcrossItems(
      invoiceItems: List[InvoiceItem],
      taxationItems: List[TaxationItem],
      adjustments: List[InvoiceItemAdjustment],
      totalRefundAmount: BigDecimal,
      refundGuid: String,
  ): List[InvoiceItemAdjustmentWrite] = {

    def buildInvoiceItemAdjustments(
        invoiceItem: InvoiceItem,
        amountToRefund: BigDecimal,
    ): List[InvoiceItemAdjustmentWrite] = {
      // If the invoice item being adjusted has tax paid on it, it will need to be adjusted in two separate adjustments:
      // - one for the charge amount where the SourceType is "InvoiceDetail" and SourceId is the invoice item id
      // - one for the tax amount where the SourceType is "Tax" and SourceId is the taxation item id
      // https://www.zuora.com/developer/api-references/older-api/operation/Object_POSTInvoiceItemAdjustment/#!path=SourceType&t=request

      val chargeAmountToRefund = invoiceItem.ChargeAmount.min(amountToRefund)
      val chargeAdjustment = List(
        InvoiceItemAdjustmentWrite(
          invoiceItem.ChargeDate.toLocalDate,
          chargeAmountToRefund,
          refundGuid,
          invoiceItem.InvoiceId,
          "Credit",
          "InvoiceDetail",
          invoiceItem.Id,
        ),
      )

      val taxAmountToRefund = amountToRefund - chargeAmountToRefund
      if (taxAmountToRefund > invoiceItem.TaxAmount) {
        println(
          s"Unexpected state when trying to create InvoiceItem adjustment for $invoiceItem. " +
            s"Amount to refund was $amountToRefund, chargeAmountToRefund was $chargeAmountToRefund " +
            s"so taxAmountToRefund was $taxAmountToRefund but the tax on the invoice item was only ${invoiceItem.TaxAmount}",
        )
        throw new RuntimeException(s"Unexpected state when trying to create InvoiceItem adjustment for $invoiceItem")
      }

      val taxAdjustment =
        if (taxAmountToRefund > 0) {
          val Some(taxationItemId) = taxationItems.find(_.InvoiceItemId == invoiceItem.Id).map(_.Id) tap { item =>
            s"Missing taxation id for invoiceItem $invoiceItem" assert item.isDefined
          }

          List(
            InvoiceItemAdjustmentWrite(
              LocalDate.now(),
              taxAmountToRefund,
              refundGuid,
              invoiceItem.InvoiceId,
              "Credit",
              "Tax",
              taxationItemId,
            ),
          )
        } else Nil

      chargeAdjustment ++ taxAdjustment
    }

    @tailrec def loop(
        remainingAmountToRefund: BigDecimal,
        remainingItems: List[InvoiceItem],
        accumulatedAdjustments: List[InvoiceItemAdjustmentWrite],
    ): List[InvoiceItemAdjustmentWrite] = {
      remainingItems match {
        case Nil =>
          accumulatedAdjustments
        case nextItem :: tail =>
          availableAmount(nextItem, adjustments) match {
            case Some(availableRefundableAmount) =>
              if (availableRefundableAmount >= remainingAmountToRefund)
                buildInvoiceItemAdjustments(nextItem, remainingAmountToRefund) ++ accumulatedAdjustments
              else {
                loop(
                  remainingAmountToRefund - availableRefundableAmount,
                  tail,
                  buildInvoiceItemAdjustments(nextItem, availableRefundableAmount) ++ accumulatedAdjustments,
                )
              }
            case None =>
              loop(remainingAmountToRefund, tail, accumulatedAdjustments)
          }
      }
    }

    loop(totalRefundAmount, invoiceItems, List.empty[InvoiceItemAdjustmentWrite])
  }

  def applyRefundOverItemAdjustments(invoiceItems: List[InvoiceItemAdjustmentWrite]): List[AdjustmentResult] =
    post[List[AdjustmentResult]](
      s"$zuoraApiHost/v1/action/create",
      write(
        InvoiceItemAdjustmentsWriteRequest(
          objects = invoiceItems,
          `type` = "InvoiceItemAdjustment",
        ),
      ),
    )

  def joinInvoiceWithInvoiceItemsOnInvoiceIdKey(
      invoices: List[Invoice],
      itemsByInvoiceId: Map[String, List[InvoiceItem]],
  ): List[(String, Invoice, List[InvoiceItem])] = {
    invoices.map(invoice => (invoice.Id, invoice, itemsByInvoiceId.get(invoice.Id))) collect {
      case (invoiceId, invoice, Some(invoiceItems)) => (invoiceId, invoice, invoiceItems)
    }
  }

  /** Select correct invoice to apply refund to */
  def decideRelevantInvoice(
      invoices: List[Invoice],
      itemsByInvoiceId: Map[String, List[InvoiceItem]],
  ): (String, Invoice, List[InvoiceItem]) = {
    joinInvoiceWithInvoiceItemsOnInvoiceIdKey(invoices, itemsByInvoiceId).iterator
      .filter({ case (_, invoice, _) => invoice.Status == "Posted" })
      .filter({ case (_, invoice, _) => invoice.Amount > 0 })
      .maxBy({ case (_, invoice, _) => invoice.TargetDate })
  }

  def getInvoiceItemAdjustments(invoiceId: String): List[InvoiceItemAdjustment] =
    post[InvoiceItemAdjustmentsQueryResult](
      s"$zuoraApiHost/v1/action/query",
      s"""{"queryString": "select Id, InvoiceId, InvoiceItemName, SourceId, SourceType, Status, Type, Amount FROM InvoiceItemAdjustment where InvoiceId = '$invoiceId'"}""",
    ).records

  /** Zuora uses Half Up rounding to two decimal places with rounding increment of 0.1 Corresponds to rounding rules
    * specified under Zuora | Billing Settings | Customize Currencies
    */
  def roundHalfUp(x: BigDecimal): BigDecimal = x.setScale(2, BigDecimal.RoundingMode.HALF_UP)

  // https://knowledgecenter.zuora.com/Billing/Billing_and_Payments/TB_Rounding_and_Precision
  def roundAdjustments(
      adjustments: List[InvoiceItemAdjustmentWrite],
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
