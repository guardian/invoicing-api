package com.gu.invoicing.refund

import Program._
import Model._
import Impl._
import scala.util.chaining._
import upickle.default._
import com.gu.spy._

/**
 * Run refund process as CLI application
 *
 * How to run:
 *   export Stage=CODE
 *   export AWS_ACCESS_KEY_ID=*********
 *   export AWS_SECRET_ACCESS_KEY=*********
 *   export AWS_SESSION_TOKEN=*********
 *   export AWS_SECURITY_TOKEN=*********
 *
 *   run {"subscriptionName":"A-S00045160","refund":0.1} {"subscriptionName":"A-S00045160","refund":0.1}
 *
 *   If using IntelliJ Run Configuration then escape double quotes:
 *     {\"subscriptionName\":\"A-S00045160\",\"refund\":0.1}
 */
object Cli {
  def main(args: Array[String]): Unit = {
    val start = System.nanoTime()

    args
      .map(read[RefundInput](_))
      .foreach { refundInput =>
        refundInput
          .tap  { v => println(v.spy) }
          .pipe { program }
          .tap  { v => println(v.spy) }

        println(s"Time: ${(System.nanoTime() - start) / 1000000000.0} seconds")
    }
  }
}
