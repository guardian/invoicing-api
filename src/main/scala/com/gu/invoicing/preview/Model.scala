package com.gu.invoicing.preview

import java.time.{DayOfWeek, LocalDate}
import com.gu.invoicing.common.JsonSupport

object Model extends JsonSupport {
  // ************************************************************************
  // Program input and output models
  // ************************************************************************
  case class PreviewInput(
    subscriptionName: String,
    startDate: LocalDate,
    endDate: LocalDate,
  )
  object PreviewInput {
    def apply(apiGatewayInput: ApiGatewayInput): PreviewInput =
      PreviewInput(
        apiGatewayInput.pathParameters.subscriptionName,
        LocalDate.parse(apiGatewayInput.queryStringParameters.startDate),
        LocalDate.parse(apiGatewayInput.queryStringParameters.endDate),
      )
  }
  case class PreviewOutput(
    subscriptionName: String,
    nextInvoiceDateAfterToday: Option[LocalDate] = None,
    rangeStartDate: LocalDate,
    rangeEndDate: LocalDate,
    publicationsWithinRange: List[Publication] = Nil
  )

  // ************************************************************************
  // Implementation detail models
  // ************************************************************************
  case class Config(
    clientId: String,
    clientSecret: String
  )
  case class AccessToken(
    access_token: String
  )
  case class RatePlanCharge(
    originalChargeId: String,
    price: Double /* Includes tax */,
    discountPercentage: Option[Double],
    effectiveStartDate: LocalDate,
    effectiveEndDate: LocalDate,
    model: Option[String], /* Used to determine discounts DiscountPercentage */
  )
  case class RatePlan(ratePlanCharges: List[RatePlanCharge])
  case class Subscription(
    accountId: String,
    ratePlans: List[RatePlan],
  )
  case class Publication(       /* Contrast with InvoiceItem                               */
    publicationDate: LocalDate, /* Date of paper printed on cover                          */
    invoiceDate: LocalDate,     /* Publication falls on this invoice                       */
    nextInvoiceDate: LocalDate, /* The invoice on which this publication would be credited */
    productName: String,        /* For example Newspaper Delivery                          */
    chargeName: String,         /* For example Sunday                                      */
    dayOfWeek: DayOfWeek,       /* Strongly typed day of publication                       */
    price: Double,              /* Charge of single publication (including tax)            */
    invoiceItemId: String,      /* InvoiceItem associated with this publication            */
  ) {
    require(publicationDate.getDayOfWeek == dayOfWeek, s"publicationDate should match dayOfWeek: $this")
  }
  case class InvoiceItem(
    id: String,
    subscriptionName: String,
    serviceStartDate: LocalDate,
    serviceEndDate: LocalDate,
    chargeAmount: Double,         /* Does NOT include tax! */
    productName: String,
    chargeName: String,
    taxAmount: Double,            /* Not provided by billing-preview but will be available on past invoice items */
    chargeId: String,             /* We use this to determine chargeAmount including tax from RatePlanCharge */
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
  implicit val ratePlanRW: ReadWriter[RatePlan] = macroRW
  implicit val ratePlanChargeRW: ReadWriter[RatePlanCharge] = macroRW
  implicit val subscriptionRW: ReadWriter[Subscription] = macroRW
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
