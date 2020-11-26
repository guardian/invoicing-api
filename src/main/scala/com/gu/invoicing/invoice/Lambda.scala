package com.gu.invoicing.invoice

import java.io.{InputStream, OutputStream}

import com.gu.invoicing.invoice.Model._
import com.gu.invoicing.invoice.Program._
import scala.concurrent.Await
import scala.concurrent.duration.Duration.Inf
import scala.util.chaining._

/**
 * Example test event for running the lambda from AWS Console
 * {
 *   "headers": {
 *     "x-identity-id": "123456qwerty"
 *   }
 * }
 */
object Lambda {
  def handleRequest(input: String): String = {
    input
      .pipe { read[ApiGatewayInput](_) }
      .headers
      .getOrElse("x-identity-id", throw new Error("x-identity-id header should be provided"))
      .pipe { InvoicesInput }
      .tap  { info[InvoicesInput]  }
      .pipe { program              }
      .pipe { Await.result(_, Inf) }
      .tap  { info[InvoicesOutput] }
      .pipe { invoicesOut => ApiGatewayOutput(200, write(invoicesOut)) }
      .pipe { apiGatewayOut => write(apiGatewayOut) }
  }
}
