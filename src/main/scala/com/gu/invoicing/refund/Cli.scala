package com.gu.invoicing.refund

import Program._
import Model._
import Impl._
import scala.util.chaining._

/** Run refund process as CLI application
  *
  * How to run: export Stage=CODE export Config='{"clientId": "*******", "clientSecret": "*******"}'
  * export AWS_ACCESS_KEY_ID=********* export AWS_SECRET_ACCESS_KEY=********* export
  * AWS_SESSION_TOKEN=********* export AWS_SECURITY_TOKEN=*********
  *
  * run {"subscriptionName":"A-S00045160","refund":0.1}
  * {"subscriptionName":"A-S00045160","refund":0.1}
  *
  * If using IntelliJ Run Configuration then escape double quotes:
  * {\"subscriptionName\":\"A-S00045160\",\"refund\":0.1}
  */
object Cli {
  def main(args: Array[String]): Unit = {
    args
      .map(read[RefundInput](_))
      .foreach { refundInput =>
        refundInput
          .tap { info[RefundInput] }
          .pipe { program }
          .tap { info[RefundOutput] }
      }
  }
}
