package com.gu.invoicing.invoice

import java.io.{InputStream, OutputStream}

import com.gu.invoicing.invoice.Log._
import com.gu.invoicing.invoice.Model._
import com.gu.invoicing.invoice.Program._
import scala.concurrent.Await
import scala.concurrent.duration.Duration.Inf
import scala.util.chaining._

/**
 * Example test event for running the lambda from AWS Console
 * {
 *   "pathParameters": {
 *     "accountId": "123456qwerty"
 *   }
 * }
 */
object Lambda {
  def handleRequest(input: InputStream, output: OutputStream): Unit = {
    input
      .pipe { deserialiseStream    }
      .tap  { info[InvoicesInput]  }
      .pipe { program              }
      .pipe { Await.result(_, Inf) }
      .tap  { info[InvoicesOutput] }
      .pipe { serialiseToStream    }

    def deserialiseStream(inputStream: InputStream): InvoicesInput = {
      inputStream
        .pipe { read[ApiGatewayInput](_) }
        .pathParameters
        .accountId
        .pipe { InvoicesInput }
    }
    def serialiseToStream(invoicesOutput: InvoicesOutput): Unit  = {
      invoicesOutput
        .pipe { invoicesOut => ApiGatewayOutput(200, write(invoicesOut)) }
        .pipe { apiGatewayOut => write(apiGatewayOut) }
        .pipe { _.getBytes }
        .pipe { output.write }
    }
  }
}
