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
  case class Config(clientId: String, clientSecret: String)
  case class AccessToken(access_token: String)
  case class Subscription(accountId: String, accountNumber: String, subscriptionNumber: String)
  case class Invoice(Id: String, InvoiceNumber: String, Amount: BigDecimal, Balance: BigDecimal, PaymentAmount: BigDecimal, TargetDate: LocalDate, InvoiceDate: LocalDate, Status: String)
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
    Amount: BigDecimal,
    Comment: String,
    Status: String,
    Gateway: String,
    MethodType: String,
    GatewayResponseCode: String,
    Id: String
  )
  case class InvoiceItemAdjustmentWrite(
    AdjustmentDate: LocalDate,
    Amount: BigDecimal,
    Comments: String,
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
    Amount: BigDecimal,
  )
  case class InvoiceItemAdjustmentsQueryResult(records: List[InvoiceItemAdjustment])
  case class Metrics(balance: BigDecimal, totalInvoiceBalance: BigDecimal, creditBalance: BigDecimal)
  case class Account(metrics: Metrics)

  implicit val bigDecimalRW: ReadWriter[BigDecimal] = readwriter[Double].bimap[BigDecimal](_.toDouble, double => BigDecimal(double.toString))
  implicit val localDateRW: ReadWriter[LocalDate] = readwriter[String].bimap[LocalDate](_.toString, LocalDate.parse(_, ofPattern("yyyy-MM-dd")))
  implicit val localDateTimeRW: ReadWriter[LocalDateTime] = readwriter[String].bimap[LocalDateTime](_.toString, LocalDateTime.parse(_, DateTimeFormatter.ISO_OFFSET_DATE_TIME)) // ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
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
    refund: BigDecimal,
    guid: String = UUID.randomUUID().toString,
    message: String = "Start processing refund"
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

  // https://docs.aws.amazon.com/apigateway/latest/developerguide/set-up-lambda-proxy-integrations.html#api-gateway-simple-proxy-for-lambda-input-format
  case class ApiGatewayInput(body: String)

  // https://aws.amazon.com/premiumsupport/knowledge-center/malformed-502-api-gateway/
  case class ApiGatewayOutput(statusCode: Int, body: String)

  implicit val refundInputRW: ReadWriter[RefundInput] = macroRW
  implicit val refundOutputRW: ReadWriter[RefundOutput] = macroRW
  implicit val awsBodyRW: ReadWriter[ApiGatewayInput] = macroRW
  implicit val apiGatewayOutputRW: ReadWriter[ApiGatewayOutput] = macroRW
}
