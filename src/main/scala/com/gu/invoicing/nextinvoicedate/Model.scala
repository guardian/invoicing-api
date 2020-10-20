package com.gu.invoicing.nextinvoicedate

import java.time.LocalDate
import com.gu.invoicing.common.JsonSupport

object Model extends JsonSupport {
  case class Config(clientId: String, clientSecret: String)
  case class AccessToken(access_token: String)

  case class Subscription(accountId: String)

  case class InvoiceItem(
    id: String,
    subscriptionName: String,
    serviceStartDate: LocalDate,
    serviceEndDate: LocalDate,
    chargeAmount: Double,
    productName: String,
  )

  case class BillingPreview(
    accountId: String,
    invoiceItems: List[InvoiceItem],
  )

  implicit val subscription: ReadWriter[Subscription] = macroRW
  implicit val invoiceItem: ReadWriter[InvoiceItem] = macroRW
  implicit val billingPreview: ReadWriter[BillingPreview] = macroRW

  case class SubscriptionName(subscriptionName: String)
  case class ApiGatewayInput(
    pathParameters: SubscriptionName,
    headers: Map[String, String]
  )
  case class NextInvoiceDateInput(subscriptionName: String)
  object NextInvoiceDateInput {
    def apply(apiGatewayInput: ApiGatewayInput): NextInvoiceDateInput =
      NextInvoiceDateInput(apiGatewayInput.pathParameters.subscriptionName)
  }
  case class NextInvoiceDateOutput(nextInvoiceDate: Option[LocalDate] = None)
  case class ApiGatewayOutput(
    statusCode: Int,
    body: String,
  )

  implicit val subscriptionNumber: ReadWriter[SubscriptionName] = macroRW
  implicit val awsBodyRW: ReadWriter[ApiGatewayInput] = macroRW
  implicit val apiGatewayOutputRW: ReadWriter[ApiGatewayOutput] = macroRW
  implicit val nextInvoiceDateInput: ReadWriter[NextInvoiceDateInput] = macroRW
  implicit val nextInvoiceDateOutput: ReadWriter[NextInvoiceDateOutput] = macroRW
}
