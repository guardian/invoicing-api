package com.gu.invoicing.refundErroneousPayment

import com.gu.invoicing.common.JsonSupport

import java.time.LocalDate

/** Data models and JSON codecs
  */
object Model extends JsonSupport {

  case class Config(clientId: String, clientSecret: String)

  case class AccessToken(access_token: String)

  case class Invoice(
      Id: String,
      InvoiceNumber: String,
      Amount: BigDecimal,
      Balance: BigDecimal,
      PaymentAmount: BigDecimal,
      TargetDate: LocalDate,
      InvoiceDate: LocalDate,
      Status: String
  )

  case class InvoiceQueryResult(records: List[Invoice])

  case class Metrics(
      balance: BigDecimal,
      totalInvoiceBalance: BigDecimal,
      creditBalance: BigDecimal
  )
  case class Account(metrics: Metrics)

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

  implicit val configRW: ReadWriter[Config] = macroRW
  implicit val accessTokenRW: ReadWriter[AccessToken] = macroRW
  implicit val invoiceRW: ReadWriter[Invoice] = macroRW
  implicit val invoiceQueryResultRW: ReadWriter[InvoiceQueryResult] = macroRW
  implicit val refundResultRW: ReadWriter[RefundResult] = macroRW
  implicit val refundRW: ReadWriter[Refund] = macroRW
  implicit val metricsRW: ReadWriter[Metrics] = macroRW
  implicit val accountRW: ReadWriter[Account] = macroRW

  case class RefundInput(
      accountId: String,
      invoiceNumber: String,
      paymentId: String,
      invoiceAmount: BigDecimal,
      comment: String,
      message: String = "Start processing refund"
  )

  case class RefundOutput(
      accountId: String,
      paymentId: String,
      invoiceAmount: BigDecimal,
      invoiceNumber: String,
      balancingInvoiceNumber: String,
      message: String = "Successful refund"
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
