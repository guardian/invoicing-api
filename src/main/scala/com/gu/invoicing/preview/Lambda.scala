package com.gu.invoicing.preview

import com.amazonaws.services.lambda.runtime.events.{APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent}
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.gu.invoicing.common.HttpHelper.okResponse
import com.gu.invoicing.preview.Model._
import com.gu.invoicing.preview.Program._

import scala.util.chaining._

/** Example test event for running the lambda from AWS Console { "pathParameters": { "subscriptionName": "A-S00000000"
  * }, "queryStringParameters": { "startDate": "2020-10-10", "endDate": "2020-10-20" } }
  */
object Lambda extends RequestHandler[APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent] {
  def handleRequest(input: APIGatewayProxyRequestEvent, context: Context): APIGatewayProxyResponseEvent = {
    PreviewInput(input)
      .tap { info[PreviewInput] }
      .pipe { program }
      .tap { info[PreviewOutput] }
      .pipe { output => okResponse(write(output)) }
  }
}
