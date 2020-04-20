package com.gu.invoicing.refund

import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatter.ofPattern
import java.time.{LocalDate, LocalDateTime}
import java.util.UUID

import upickle.default._

/**
 * Data models and JSON codecs
 */
object Model {
  implicit class StringAssert(specification: String) {
    def assert(predicate: Boolean): Unit = Predef.assert(predicate, specification)
  }


  case class Oauth(clientId: String, clientSecret: String)
  case class ZuoraDatalakeExport(oauth: Oauth)
  case class Config(stage: String, baseUrl: String, zuoraDatalakeExport: ZuoraDatalakeExport)
  case class AccessToken(access_token: String)
  case class Subscription(accountId: String, accountNumber: String, subscriptionNumber: String)
  case class Invoice(Id: String, InvoiceNumber: String, Amount: Double, Balance: Double, PaymentAmount: Double, TargetDate: LocalDate, InvoiceDate: LocalDate, Status: String)
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
    ChargeAmount: Double,
    SubscriptionNumber: String,
  )
  case class InvoiceItemQueryResult(records: List[InvoiceItem])
  case class InvoiceItems(invoiceItems: List[InvoiceItem])
  case class InvoicePayment(PaymentId: String, InvoiceId: String)
  case class InvoicePaymentQueryResult(records: List[InvoicePayment])
  case class RefundResult(Id: String)
  case class Refund(
    RefundNumber: String,
    GatewayState: String,
    RefundDate: LocalDate,
    ReasonCode: String,
    GatewayResponse: String,
    Amount: Double,
    Comment: String,
    Status: String,
    Gateway: String,
    MethodType: String,
    GatewayResponseCode: String,
    Id: String
  )
  case class InvoiceItemAdjustmentWrite(
    AdjustmentDate: LocalDate,
    Amount: Double,
    Comments: String,
    //    InvoiceNumber: String,
    InvoiceId: String,
    Type: String,
    SourceType: String,
    SourceId: String
  )
  case class InvoiceItemAdjustmentsWriteRequest(
    objects: List[InvoiceItemAdjustmentWrite],
    `type`: String
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
    Amount: Double,
  )
  case class InvoiceItemAdjustmentsQueryResult(records: List[InvoiceItemAdjustment])
  case class Metrics(balance: Double, totalInvoiceBalance: Double, creditBalance: Double)
//  case class Account(Id: String, AccountNumber: String, Balance: Double, CreditBalance: Double)
  case class Account(metrics: Metrics)

  //  DateTimeFormatter.ISO_OFFSET_DATE_TIME
  implicit val localDateRW: ReadWriter[LocalDate] = readwriter[String].bimap[LocalDate](_.toString, LocalDate.parse(_, ofPattern("yyyy-MM-dd")))
  //  implicit val localDateTimeRW: ReadWriter[LocalDateTime] = readwriter[String].bimap[LocalDateTime](_.toString, LocalDateTime.parse(_, ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")))
  implicit val localDateTimeRW: ReadWriter[LocalDateTime] = readwriter[String].bimap[LocalDateTime](_.toString, LocalDateTime.parse(_, DateTimeFormatter.ISO_OFFSET_DATE_TIME))
  implicit val oauthRW: ReadWriter[Oauth] = macroRW
  implicit val zuoraDatalakeExportRW: ReadWriter[ZuoraDatalakeExport] = macroRW
  implicit val configRW: ReadWriter[Config] = macroRW
  implicit val accessTokenRW: ReadWriter[AccessToken] = macroRW
  implicit val subscriptionRW: ReadWriter[Subscription] = macroRW
  implicit val invoiceRW: ReadWriter[Invoice] = macroRW
  implicit val invoiceQueryResultRW: ReadWriter[InvoiceQueryResult] = macroRW
  implicit val invoiceItemRW: ReadWriter[InvoiceItem] = macroRW
  implicit val invoiceItemsRW: ReadWriter[InvoiceItems] = macroRW
  implicit val invoiceItemQueryResultRW: ReadWriter[InvoiceItemQueryResult] = macroRW
  implicit val invoicePaymentRW: ReadWriter[InvoicePayment] = macroRW
  implicit val invoicePaymentQueryResultRW: ReadWriter[InvoicePaymentQueryResult] = macroRW
  implicit val refundResultRW: ReadWriter[RefundResult] = macroRW
  implicit val refundRW: ReadWriter[Refund] = macroRW
  implicit val invoiceItemAdjustmentRW: ReadWriter[InvoiceItemAdjustmentWrite] = macroRW
  implicit val createInvoiceItemAdjustmentsRW: ReadWriter[InvoiceItemAdjustmentsWriteRequest] = macroRW
  implicit val invoiceItemAdjustmentReadRW: ReadWriter[InvoiceItemAdjustment] = macroRW
  implicit val invoiceItemAdjustmentsQueryResultRW: ReadWriter[InvoiceItemAdjustmentsQueryResult] = macroRW
  implicit val metricsRW: ReadWriter[Metrics] = macroRW
  implicit val accountRW: ReadWriter[Account] = macroRW
  implicit val adjustmentResultRW: ReadWriter[AdjustmentResult] = macroRW

  case class RefundInput(
    subscriptionName: String,
    refund: Double,
    guid: String = UUID.randomUUID().toString,
    message: String = "Start processing refund"
  )
  case class RefundOutput(
    subscriptionName: String,
    refundAmount: Double,
    invoiceId: String,
    paymentId: String,
    adjustments: List[InvoiceItemAdjustmentWrite],
    guid: String, // written to Refund.Comment field and InvoiceItemAdjustment.Comment to tie them together
    message: String = "Successful refund",
  )

  implicit val refundInputRW: ReadWriter[RefundInput] = macroRW
  implicit val refundOutputRW: ReadWriter[RefundOutput] = macroRW
}
