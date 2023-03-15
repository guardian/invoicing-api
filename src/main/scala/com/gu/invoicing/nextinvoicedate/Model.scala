package com.gu.invoicing.nextinvoicedate

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.gu.invoicing.common.HttpHelper.pathParameterOrThrow

import java.time.LocalDate
import com.gu.invoicing.common.JsonSupport

object Model extends JsonSupport {
  case class Subscription(accountId: String)

  case class InvoiceItem(
      id: String,
      subscriptionName: String,
      serviceStartDate: LocalDate,
      serviceEndDate: LocalDate,
      chargeAmount: Double,
      productName: String
  )

  case class BillingPreview(
      accountId: String,
      invoiceItems: List[InvoiceItem]
  )

  implicit val subscription: ReadWriter[Subscription] = macroRW
  implicit val invoiceItem: ReadWriter[InvoiceItem] = macroRW
  implicit val billingPreview: ReadWriter[BillingPreview] = macroRW

  case class NextInvoiceDateInput(subscriptionName: String)
  object NextInvoiceDateInput {
    def apply(apiGatewayInput: APIGatewayProxyRequestEvent): NextInvoiceDateInput =
      NextInvoiceDateInput(pathParameterOrThrow(apiGatewayInput, "subscriptionName"))
  }
  case class NextInvoiceDateOutput(nextInvoiceDate: Option[LocalDate] = None)

  implicit val nextInvoiceDateInput: ReadWriter[NextInvoiceDateInput] = macroRW
  implicit val nextInvoiceDateOutput: ReadWriter[NextInvoiceDateOutput] = macroRW
}
