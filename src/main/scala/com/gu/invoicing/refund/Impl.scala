package com.gu.invoicing.refund

import com.gu.invoicing.common.ZuoraAuth.{accessToken, zuoraApiHost}

import java.time.LocalDate
import com.gu.invoicing.common.Http

import scala.annotation.tailrec
import Model._
import pprint.log

import scala.util.chaining._

/** Zuora API client and implementation details
  */
object Impl {
  def getSubscription(name: String): Subscription = {
    Http(s"$zuoraApiHost/v1/subscriptions/$name")
      .header("Authorization", s"Bearer $accessToken")
      .asString
      .body
      .pipe(read[Subscription](_))
  }

  def getAccountBalance(accountId: String): BigDecimal = {
    Http(s"$zuoraApiHost/v1/accounts/$accountId")
      .header("Authorization", s"Bearer $accessToken")
      .asString
      .body
      .pipe(read[Account](_))
      .metrics
      .balance
  }

  def getInvoices(accountId: String): List[Invoice] = {
    Http(s"$zuoraApiHost/v1/action/query")
      .header("Authorization", s"Bearer $accessToken")
      .header("Content-Type", "application/json")
      .postData(
        s"""{"queryString": "select Id, Amount, Balance, InvoiceDate, InvoiceNumber, PaymentAmount, TargetDate, Status from Invoice where AccountId = '$accountId'"}"""
      )
      .method("POST")
      .asString
      .body
      .pipe(read[InvoiceQueryResult](_))
      .records
  }

  def getInvoiceItems(invoiceId: String): List[InvoiceItem] = {
    Http(s"$zuoraApiHost/v1/invoices/$invoiceId/items")
      .header("Authorization", s"Bearer $accessToken")
      .asString
      .body
      .pipe(read[InvoiceItems](_))
      .invoiceItems
  }

  def getItemsByInvoice(subscriptionName: String): Map[String, List[InvoiceItem]] = {
    Http(s"$zuoraApiHost/v1/action/query")
      .header("Authorization", s"Bearer $accessToken")
      .header("Content-Type", "application/json")
      .postData(
        s"""{"queryString": "select Id, ChargeAmount, ChargeDate, ChargeName, ChargeNumber, InvoiceId, ProductName, ServiceEndDate, ServiceStartDate, SubscriptionNumber, UnitPrice FROM InvoiceItem where SubscriptionNumber = '$subscriptionName'"}""".stripMargin
      )
      .method("POST")
      .asString
      .body
      .pipe { body =>
        System.out.println(s"Response from getItemsByInvoice query was $body")
        read[InvoiceItemQueryResult](body)
      }
      .records
      .groupBy(_.InvoiceId)
  }

  def getInvoicePaymentId(invoiceId: String): Option[String] = {
    Http(s"$zuoraApiHost/v1/action/query")
      .header("Authorization", s"Bearer $accessToken")
      .header("Content-Type", "application/json")
      .postData(
        s"""{"queryString": "select Id, invoiceId, paymentId, CreatedDate from InvoicePayment where invoiceId = '$invoiceId'"}"""
      )
      .method("POST")
      .asString
      .body
      .pipe(read[InvoicePaymentQueryResult](_))
      .records
      .sortBy(_.CreatedDate)
      .reverse
      .headOption
      .map(_.PaymentId)
  }

  def createRefundObject(amount: BigDecimal, paymentId: String, comment: String): String = {
    Http(s"$zuoraApiHost/v1/object/refund")
      .header("Authorization", s"Bearer $accessToken")
      .header("Content-Type", "application/json")
      .postData(s"""
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
    Http(s"$zuoraApiHost/v1/object/refund/$refundId")
      .header("Authorization", s"Bearer $accessToken")
      .asString
      .body
      .pipe(read[Refund](_))
      .Status
  }

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

  def applyRefundOverItemAdjustments(
      invoiceItems: List[InvoiceItemAdjustmentWrite]
  ): List[AdjustmentResult] = {
    val url = s"$zuoraApiHost/v1/action/create"
    val body = write(
      InvoiceItemAdjustmentsWriteRequest(
        objects = invoiceItems,
        `type` = "InvoiceItemAdjustment"
      )
    )

    log(s"Calling $url with body $body")

    Http(url)
      .header("Authorization", s"Bearer $accessToken")
      .header("Content-Type", "application/json")
      .postData(
        body
      )
      .method("POST")
      .asString
      .body
      .pipe{ responseBody =>
        System.out.println(s"Response was $responseBody")
        read[List[AdjustmentResult]](responseBody)
      }
  }

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

  def getInvoiceItemAdjustments(invoiceId: String): List[InvoiceItemAdjustment] = {
    Http(s"$zuoraApiHost/v1/action/query")
      .header("Authorization", s"Bearer $accessToken")
      .header("Content-Type", "application/json")
      .postData(
        s"""{"queryString": "select Id, InvoiceId, InvoiceItemName, SourceId, SourceType, Status, Type, Amount FROM InvoiceItemAdjustment where InvoiceId = '$invoiceId'"}"""
      )
      .method("POST")
      .asString
      .body
      .pipe(read[InvoiceItemAdjustmentsQueryResult](_))
      .records
  }

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

}
