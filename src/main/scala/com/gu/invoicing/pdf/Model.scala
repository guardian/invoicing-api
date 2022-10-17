package com.gu.invoicing.pdf

import com.gu.invoicing.common.JsonSupport

import java.util.Currency

object Model extends JsonSupport {
  case class Config(clientId: String, clientSecret: String)
  case class AccessToken(access_token: String)

  case class Invoice(
      Id: String,
      AccountId: String,
      Body: String /* Base64 encoded PDF */
  )
  case class BasicInfo(IdentityId__c: String)
  case class Account(IdentityId__c: String, InvoiceTemplateId: String, Currency: Currency)
  case class InvoiceFile(pdfFileUrl: String)
  case class InvoiceFiles(invoiceFiles: List[InvoiceFile])
  case class PutResponse(Success: Boolean, Id: String)

  implicit val configRW: ReadWriter[Config] = macroRW
  implicit val accessTokenRW: ReadWriter[AccessToken] = macroRW
  implicit val invoiceRW: ReadWriter[Invoice] = macroRW
  implicit val BasicInfoRW: ReadWriter[BasicInfo] = macroRW
  implicit val AccountRW: ReadWriter[Account] = macroRW
  implicit val InvoiceFileRW: ReadWriter[InvoiceFile] = macroRW
  implicit val InvoiceFileIdsRW: ReadWriter[InvoiceFiles] = macroRW
  implicit val PutResponseRW: ReadWriter[PutResponse] = macroRW

  // https://docs.aws.amazon.com/apigateway/latest/developerguide/set-up-lambda-proxy-integrations.html#api-gateway-simple-proxy-for-lambda-input-format
  case class InvoiceId(invoiceId: String)
  case class ApiGatewayInput(
      pathParameters: InvoiceId,
      headers: Map[String, String]
  )
  case class PdfInput(invoiceId: String, identityId: String)
  object PdfInput {
    def apply(apiGatewayInput: ApiGatewayInput): PdfInput = {
      new PdfInput(
        apiGatewayInput.pathParameters.invoiceId,
        apiGatewayInput.headers
          .getOrElse("x-identity-id", throw new Error("x-identity-id header should be provided"))
      )
    }
  }

  /** https://docs.aws.amazon.com/apigateway/latest/developerguide/lambda-proxy-binary-media.html
    * https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-payload-encodings-configure-with-console.html
    * https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-resource-apigateway-restapi.html#cfn-apigateway-restapi-binarymediatypes
    *
    * { "status": 200, "body": "base64EncodedByteArray", "isBase64Encoded":true, "headers": {
    * "Content-Type":"application/pdf;charset=UTF-8" } }
    */
  case class ApiGatewayOutput(
      statusCode: Int,
      body: String, // base64 encoded byte array representing PDF
      isBase64Encoded: Boolean,
      headers: Map[String, String]
  )

  implicit val InvoiceIdRW: ReadWriter[InvoiceId] = macroRW
  implicit val awsBodyRW: ReadWriter[ApiGatewayInput] = macroRW
  implicit val apiGatewayOutputRW: ReadWriter[ApiGatewayOutput] = macroRW
  implicit val PdfInputRW: ReadWriter[PdfInput] = macroRW
}
