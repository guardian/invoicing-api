package com.gu.invoicing.nextinvoicedate

import com.amazonaws.services.lambda.runtime.events.{APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent}
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.gu.invoicing.common.HttpHelper.okResponse
import com.gu.invoicing.nextinvoicedate.Model._
import com.gu.invoicing.nextinvoicedate.Program._

import scala.util.chaining._

/** Example test event for running the lambda from AWS Console { "pathParameters": { "subscriptionNumber": "A-S00000000"
  * } }
  */
object Lambda extends RequestHandler[APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent] {
  def handleRequest(input: APIGatewayProxyRequestEvent, context: Context): APIGatewayProxyResponseEvent = {
    input.getBody
      .pipe { NextInvoiceDateInput.apply }
      .tap { info[NextInvoiceDateInput] }
      .pipe { program }
      .tap { info[NextInvoiceDateOutput] }
      .pipe { output => okResponse(write(output)) }
  }
}
