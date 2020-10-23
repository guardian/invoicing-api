package com.gu.invoicing.preview

import java.io.{InputStream, OutputStream}
import com.gu.invoicing.preview.Model._
import com.gu.invoicing.preview.Impl._
import com.gu.invoicing.preview.Program._
import scala.util.chaining._
import com.gu.spy._

/**
 * Example test event for running the lambda from AWS Console
 * {
 *   "pathParameters": {
 *     "subscriptionNumber": "A-S00000000"
 *   },
 *   "queryStringParameters": {
 *     "startDate": "2020-10-10",
 *     "endDate": "2020-10-20"
 *   }
 * }
 */
object Lambda {
  def handleRequest(input: InputStream, output: OutputStream): Unit =
    input
      .pipe { read[ApiGatewayInput](_) }
      .pipe { PreviewInput.apply }
      .tap  { info[PreviewInput] }
      .pipe { program }
      .tap  { info[PreviewOutput] }
      .pipe { invoiceDateOutput => ApiGatewayOutput(200, write(invoiceDateOutput)) }
      .pipe { write(_) }
      .pipe { _.getBytes }
      .pipe { output.write }
}
