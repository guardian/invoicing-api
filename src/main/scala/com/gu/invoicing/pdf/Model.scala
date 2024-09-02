package com.gu.invoicing.pdf

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.gu.invoicing.common.HttpHelper.{headerOrThrow, pathParameterOrThrow}
import com.gu.invoicing.common.JsonSupport

import java.util.Currency
import scala.jdk.CollectionConverters.MapHasAsScala

object Model extends JsonSupport {
  case class Config(clientId: String, clientSecret: String)
  case class AccessToken(access_token: String)

  case class Invoice(
      Id: String,
      AccountId: String,
      Body: String, /* Base64 encoded PDF */
  )
  case class BasicInfo(IdentityId__c: String)
  case class BillingAndPayment(currency: String)
  case class SoldToContact(country: String)
  case class Account(
      basicInfo: BasicInfo,
      billingAndPayment: BillingAndPayment,
      soldToContact: SoldToContact,
  )
  case class InvoiceFile(pdfFileUrl: String)
  case class InvoiceFiles(invoiceFiles: List[InvoiceFile])
  case class PutResponse(Success: Boolean, Id: String)

  implicit val configRW: ReadWriter[Config] = macroRW
  implicit val accessTokenRW: ReadWriter[AccessToken] = macroRW
  implicit val invoiceRW: ReadWriter[Invoice] = macroRW
  implicit val BasicInfoRW: ReadWriter[BasicInfo] = macroRW
  implicit val BillingAndPaymentRW: ReadWriter[BillingAndPayment] = macroRW
  implicit val SoldToContactRW: ReadWriter[SoldToContact] = macroRW
  implicit val AccountRW: ReadWriter[Account] = macroRW
  implicit val InvoiceFileRW: ReadWriter[InvoiceFile] = macroRW
  implicit val InvoiceFileIdsRW: ReadWriter[InvoiceFiles] = macroRW
  implicit val PutResponseRW: ReadWriter[PutResponse] = macroRW

  case class PdfInput(invoiceId: String, identityId: String)
  object PdfInput {
    def apply(apiGatewayInput: APIGatewayProxyRequestEvent): PdfInput = {
      new PdfInput(
        pathParameterOrThrow(apiGatewayInput, "invoiceId"),
        headerOrThrow(apiGatewayInput, "x-identity-id"),
      )
    }
  }

  implicit val PdfInputRW: ReadWriter[PdfInput] = macroRW
}
