package com.gu.invoicing.pdf

import java.io.{InputStream, OutputStream}
import com.gu.invoicing.pdf.Model._
import com.gu.invoicing.pdf.Impl._
import com.gu.invoicing.pdf.Program._
import scala.util.chaining._
import com.gu.spy._

/** Example test event for running the lambda from AWS Console { "headers": { "x-identity-id":
  * "1000001" }, "pathParameters": { "invoiceId": "1a2s3d4f5g6h7j8k" } }
  */
object Lambda {
  def handleRequest(input: String): String =
    input
      .pipe { read[ApiGatewayInput](_) }
      .pipe { PdfInput.apply }
      .tap { info[PdfInput] }
      .pipe { program }
      .pipe {
        ApiGatewayOutput(
          200,
          _,
          true,
          Map("Content-Type" -> "application/pdf;charset=UTF-8") ++ noCache
        )
      }
      .pipe { write(_) }
}
