package com.gu.invoicing.refund

import java.io.{InputStream, OutputStream}

import Model._
import Impl._
import Program._
import scala.util.chaining._
import upickle.default._
import com.typesafe.scalalogging.LazyLogging

/**
 * Run refund process as AWS lambda.
 * https://aws.amazon.com/blogs/compute/writing-aws-lambda-functions-in-scala/
 *
 * Example test event:
 *   {"subscriptionName":"A-S00045160","refund":0.1}
 */
object Lambda extends LazyLogging {
  def handleRequest(input: InputStream, output: OutputStream) = {
    val refundInput = read[RefundInput](input)

    refundInput
        .tap { in => logger.info(write(in)) }
        .pipe { program }
        .tap { out => logger.info(write(out)) }

    writeBinaryTo(refundInput, output)
  }
}
