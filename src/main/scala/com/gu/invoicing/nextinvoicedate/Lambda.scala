package com.gu.invoicing.nextinvoicedate

import java.io.{InputStream, OutputStream}
import com.gu.invoicing.nextinvoicedate.Model._
import com.gu.invoicing.nextinvoicedate.Impl._
import com.gu.invoicing.nextinvoicedate.Program._
import scala.util.chaining._
import com.gu.spy._

/** Example test event for running the lambda from AWS Console { "pathParameters": {
  * "subscriptionNumber": "A-S00000000" } }
  */
object Lambda {
  def handleRequest(input: String): String =
    input
      .pipe { read[ApiGatewayInput](_) }
      .pipe { NextInvoiceDateInput.apply }
      .tap { info[NextInvoiceDateInput] }
      .pipe { program }
      .tap { info[NextInvoiceDateOutput] }
      .pipe { invoiceDateOutput => ApiGatewayOutput(200, write(invoiceDateOutput)) }
      .pipe { write(_) }
}
