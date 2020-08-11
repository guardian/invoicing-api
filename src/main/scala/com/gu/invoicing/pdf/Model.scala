package com.gu.invoicing.pdf

/**
 * Needed for upickle to handle optional fields
 * http://www.lihaoyi.com/upickle/#CustomConfiguration
 */
class OptionPickler extends upickle.AttributeTagged {
  implicit def optionWriter[T: Writer]: Writer[Option[T]] =
    implicitly[Writer[T]].comap[Option[T]] {
      case None => null.asInstanceOf[T]
      case Some(x) => x
    }

  implicit def optionReader[T: Reader]: Reader[Option[T]] = {
    new Reader.Delegate[Any, Option[T]](implicitly[Reader[T]].map(Some(_))){
      override def visitNull(index: Int) = None
    }
  }
}

object Model extends OptionPickler {
  case class Config(clientId: String, clientSecret: String)
  case class AccessToken(access_token: String)

  case class Invoice(
    AccountId: String,
    Body: String /* Base64 encoded PDF */
  )
  case class BasicInfo(IdentityId__c: String)
  case class Account(basicInfo: BasicInfo)
  case class InvoiceFile(pdfFileUrl: String)
  case class InvoiceFiles(invoiceFiles: List[InvoiceFile])

  implicit val configRW: ReadWriter[Config] = macroRW
  implicit val accessTokenRW: ReadWriter[AccessToken] = macroRW
  implicit val invoiceRW: ReadWriter[Invoice] = macroRW
  implicit val BasicInfoRW: ReadWriter[BasicInfo] = macroRW
  implicit val AccountRW: ReadWriter[Account] = macroRW
  implicit val InvoiceFileRW: ReadWriter[InvoiceFile] = macroRW
  implicit val InvoiceFileIdsRW: ReadWriter[InvoiceFiles] = macroRW

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
        apiGatewayInput.headers.getOrElse("x-identity-id", throw new Error("x-identity-id header should be provided")),
      )
    }
  }

  /**
   * https://docs.aws.amazon.com/apigateway/latest/developerguide/lambda-proxy-binary-media.html
   * https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-payload-encodings-configure-with-console.html
   *
   * {
   *   "status": 200,
   *   "body": "base64EncodedByteArray",
   *   "isBase64Encoded":true,
   *   "headers": {
   *     "Content-Type":"application/pdf;charset=UTF-8"
   *   }
   * }
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
