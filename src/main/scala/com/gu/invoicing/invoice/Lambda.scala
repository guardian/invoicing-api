package com.gu.invoicing.invoice

import com.amazonaws.services.lambda.runtime.events.{APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent}
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.gu.invoicing.common.HttpHelper.{headerOrThrow, okResponse}
import com.gu.invoicing.invoice.Model._
import com.gu.invoicing.invoice.Program._

import scala.concurrent.Await
import scala.concurrent.duration.Duration.Inf
import scala.util.chaining._

/** Example test event for running the lambda from AWS Console { "headers": { "x-identity-id": "123456qwerty" } }
  */
object Lambda extends RequestHandler[APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent] {
  def handleRequest(input: APIGatewayProxyRequestEvent, context: Context): APIGatewayProxyResponseEvent = {
    headerOrThrow(input, "x-identity-id")
      .pipe { InvoicesInput }
      .tap { info[InvoicesInput] }
      .pipe { program }
      .pipe { Await.result(_, Inf) }
      .tap { info[InvoicesOutput] }
      .pipe { output => okResponse(write(output)) }
  }
}
