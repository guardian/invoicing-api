package com.gu.invoicing.refund

import com.gu.invoicing.common.JsonSupport

import java.time.{LocalDate, LocalDateTime}
import java.util.UUID

/** Data models and JSON codecs
  */
object Model extends JsonSupport {
  case class Subscription(accountId: String, accountNumber: String, subscriptionNumber: String)
  case class Invoice(
      Id: String,
      InvoiceNumber: String,
      Amount: BigDecimal,
      Balance: BigDecimal,
      PaymentAmount: BigDecimal,
      TargetDate: LocalDate,
      InvoiceDate: LocalDate,
      Status: String,
  )
  case class InvoiceQueryResult(records: List[Invoice])
  case class InvoiceItem(
      ChargeName: String,
      ServiceEndDate: LocalDate,
      Id: String,
      InvoiceId: String,
      ChargeNumber: String,
      ProductName: String,
      ServiceStartDate: LocalDate,
      ChargeDate: LocalDateTime,
      ChargeAmount: BigDecimal,
      TaxAmount: BigDecimal,
      SubscriptionNumber: String,
  ) {
    def amountWithTax = ChargeAmount + TaxAmount
  }
  case class InvoiceItemQueryResult(records: List[InvoiceItem])
  case class InvoiceItems(invoiceItems: List[InvoiceItem])
  case class TaxationItem(Id: String, InvoiceId: String, InvoiceItemId: String)
  case class TaxationItemQueryResult(records: List[TaxationItem])
  case class InvoicePayment(PaymentId: String, InvoiceId: String, CreatedDate: LocalDateTime)
  case class InvoicePaymentQueryResult(records: List[InvoicePayment])
  case class RefundResult(Id: String)
  case class Refund(
      RefundNumber: String,
      GatewayState: String,
      RefundDate: LocalDate,
      ReasonCode: String,
      GatewayResponse: String,
      Amount: BigDecimal,
      Comment: String,
      Status: String,
      Gateway: String,
      MethodType: String,
      GatewayResponseCode: String,
      Id: String,
  )
  case class InvoiceItemAdjustmentWrite(
      AdjustmentDate: LocalDate,
      Amount: BigDecimal,
      Comments: String,
      InvoiceId: String,
      Type: String,
      SourceType: String,
      SourceId: String,
  )
  case class InvoiceItemAdjustmentsWriteRequest(
      objects: List[InvoiceItemAdjustmentWrite],
      `type`: String,
  )
  // [{"Success":true,"Id":"2c92c0f87177a5ad01717e861b4d7ecf"}]
  case class AdjustmentResult(Success: Boolean, Id: String)

  case class InvoiceItemAdjustment(
      Id: String,
      InvoiceId: String,
      InvoiceItemName: String,
      SourceId: String,
      SourceType: String,
      Status: String,
      Type: String,
      Amount: BigDecimal,
  )
  case class InvoiceItemAdjustmentsQueryResult(records: List[InvoiceItemAdjustment])
  case class Metrics(
      balance: BigDecimal,
      totalInvoiceBalance: BigDecimal,
      creditBalance: BigDecimal,
  )
  case class Account(metrics: Metrics)

  sealed abstract class PaymentStatus(val status: String)

  object PaymentStatus {
    case object Draft extends PaymentStatus("Draft")

    case object Processing extends PaymentStatus("Processing")

    case object Processed extends PaymentStatus("Processed")

    case object Error extends PaymentStatus("Error")

    case object Canceled extends PaymentStatus("Canceled")

    case object Posted extends PaymentStatus("Posted")

    val all = List(Draft, Processing, Processed, Error, Canceled, Posted)

    def fromString(status: String): Option[PaymentStatus] = all.find(_.status == status)
  }

  case class Payment(
      status: PaymentStatus,
  )

  implicit val subscriptionRW: ReadWriter[Subscription] = macroRW
  implicit val invoiceRW: ReadWriter[Invoice] = macroRW
  implicit val invoiceQueryResultRW: ReadWriter[InvoiceQueryResult] = macroRW
  implicit val invoiceItemRW: ReadWriter[InvoiceItem] = macroRW
  implicit val invoiceItemsRW: ReadWriter[InvoiceItems] = macroRW
  implicit val invoiceItemQueryResultRW: ReadWriter[InvoiceItemQueryResult] = macroRW
  implicit val taxationItemRW: ReadWriter[TaxationItem] = macroRW
  implicit val taxationItemQueryResultRW: ReadWriter[TaxationItemQueryResult] = macroRW
  implicit val invoicePaymentRW: ReadWriter[InvoicePayment] = macroRW
  implicit val invoicePaymentQueryResultRW: ReadWriter[InvoicePaymentQueryResult] = macroRW
  implicit val refundResultRW: ReadWriter[RefundResult] = macroRW
  implicit val refundRW: ReadWriter[Refund] = macroRW
  implicit val invoiceItemAdjustmentRW: ReadWriter[InvoiceItemAdjustmentWrite] = macroRW
  implicit val createInvoiceItemAdjustmentsRW: ReadWriter[InvoiceItemAdjustmentsWriteRequest] =
    macroRW
  implicit val invoiceItemAdjustmentReadRW: ReadWriter[InvoiceItemAdjustment] = macroRW
  implicit val invoiceItemAdjustmentsQueryResultRW: ReadWriter[InvoiceItemAdjustmentsQueryResult] =
    macroRW
  implicit val metricsRW: ReadWriter[Metrics] = macroRW
  implicit val accountRW: ReadWriter[Account] = macroRW
  implicit val paymentStatusRW: ReadWriter[PaymentStatus] =
    readwriter[String].bimap[PaymentStatus](
      _.status,
      status => PaymentStatus.fromString(status).getOrElse(
        throw upickle.core.Abort(s"Invalid status: $status expected ${PaymentStatus.all.map(_.status)}")
      )
    )
  implicit val paymentRW: ReadWriter[Payment] = macroRW
  implicit val adjustmentResultRW: ReadWriter[AdjustmentResult] = macroRW

  case class RefundInput(
      subscriptionName: String,
      refund: BigDecimal,
      adjustInvoices: Boolean = true,
      guid: String = UUID.randomUUID().toString,
      message: String = "Start processing refund",
  )
  case class RefundOutput(
      subscriptionName: String,
      refundAmount: BigDecimal,
      invoiceId: String,
      paymentId: String,
      adjustments: List[InvoiceItemAdjustmentWrite],
      guid: String, // written to Refund.Comment field and InvoiceItemAdjustment.Comment to tie them together
      message: String = "Successful refund",
  )

  implicit val refundInputRW: ReadWriter[RefundInput] = macroRW
  implicit val refundOutputRW: ReadWriter[RefundOutput] = macroRW
}
