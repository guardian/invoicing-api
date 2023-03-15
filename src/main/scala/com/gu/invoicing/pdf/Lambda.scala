package com.gu.invoicing.pdf

import com.amazonaws.services.lambda.runtime.events.{APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent}
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.gu.invoicing.pdf.Impl._
import com.gu.invoicing.pdf.Model._
import com.gu.invoicing.pdf.Program._

import scala.jdk.CollectionConverters.MapHasAsJava
import scala.util.chaining._

/** Example test event for running the lambda from AWS Console { "headers": { "x-identity-id": "1000001" },
  * "pathParameters": { "invoiceId": "1a2s3d4f5g6h7j8k" } }
  */
object Lambda extends RequestHandler[APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent] {
  def handleRequest(input: APIGatewayProxyRequestEvent, context: Context): APIGatewayProxyResponseEvent = {
    PdfInput(input)
      .tap { info[PdfInput] }
      .pipe { program }
      .pipe { pdfOut =>
        val response = new APIGatewayProxyResponseEvent
        response.setStatusCode(200)
        response.setHeaders((Map("Content-Type" -> "application/pdf;charset=UTF-8") ++ noCache).asJava)
        response.setBody(pdfOut)
        response.setIsBase64Encoded(true)
        response
      }
  }
}
