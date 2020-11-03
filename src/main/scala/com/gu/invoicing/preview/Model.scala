package com.gu.invoicing.preview

import java.time.temporal.TemporalAdjusters
import java.time.{DayOfWeek, LocalDate}
import com.gu.invoicing.common.JsonSupport

object Model extends JsonSupport {
  // ************************************************************************
  // Program input and output models
  // ************************************************************************
  case class PreviewInput(
    subscriptionName: String,
    startDate: LocalDate,
    endDate: LocalDate
  )
  object PreviewInput {
    def apply(apiGatewayInput: ApiGatewayInput): PreviewInput =
      PreviewInput(
        apiGatewayInput.pathParameters.subscriptionName,
        LocalDate.parse(apiGatewayInput.queryStringParameters.startDate),
        LocalDate.parse(apiGatewayInput.queryStringParameters.endDate),
      )
  }
  case class Publication(
    publicationDate: LocalDate,
    invoiceDate: LocalDate,
    nextInvoiceDate: LocalDate,
    productName: String,
    chargeName: String,
    dayOfWeek: DayOfWeek,
    price: Double,
  ) {
    def previous: Publication = { /* get corresponding date of the same day last week */
      this.copy(publicationDate = this.publicationDate.`with`(TemporalAdjusters.previous(dayOfWeek)))
    }
  }
  case class PreviewOutput(
    subscriptionName: String,
    nextInvoiceDateAfterToday: Option[LocalDate] = None,
    rangeStartDate: LocalDate,
    rangeEndDate: LocalDate,
    publicationsWithRange: List[Publication] = Nil
  )

  // ************************************************************************
  // Implementation detail models
  // ************************************************************************
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
    chargeName: String,
  )
  case class BillingPreview(
    accountId: String,
    invoiceItems: List[InvoiceItem],
  )
  case class Invoice(
    id: String,
    invoiceNumber: String,
    invoiceDate: LocalDate,
    amount: BigDecimal,
    status: String,
    invoiceItems: List[InvoiceItem],
  )
  case class Invoices(
    invoices: List[Invoice],
    success: Boolean
  )

  // ************************************************************************
  // API Gateway Lambda for proxy integration input and output models
  // ************************************************************************
  case class SubscriptionName(
    subscriptionName: String
  )
  case class Range(
    startDate: String,
    endDate: String
  )
  case class ApiGatewayInput(
    pathParameters: SubscriptionName,
    queryStringParameters: Range,
    headers: Map[String, String]
  )
  case class ApiGatewayOutput(
    statusCode: Int,
    body: String,
  )

  // ************************************************************************
  // Codecs
  // ************************************************************************
  implicit val subscription: ReadWriter[Subscription] = macroRW
  implicit val invoiceItem: ReadWriter[InvoiceItem] = macroRW
  implicit val billingPreview: ReadWriter[BillingPreview] = macroRW
  implicit val invoice: ReadWriter[Invoice] = macroRW
  implicit val invoices: ReadWriter[Invoices] = macroRW
  implicit val subscriptionNumber: ReadWriter[SubscriptionName] = macroRW
  implicit val rangeRW: ReadWriter[Range] = macroRW
  implicit val awsBodyRW: ReadWriter[ApiGatewayInput] = macroRW
  implicit val apiGatewayOutputRW: ReadWriter[ApiGatewayOutput] = macroRW
  implicit val nextInvoiceDateInput: ReadWriter[PreviewInput] = macroRW
  implicit val publicationRW: ReadWriter[Publication] = macroRW
  implicit val nextInvoiceDateOutput: ReadWriter[PreviewOutput] = macroRW
}
