package com.gu.invoicing.nextinvoicedate

import scala.util.chaining._
import Impl._
import Program._
import com.gu.invoicing.nextinvoicedate.Model._
import com.gu.spy._

/**
 * Create environmental variables with Zuora OAuth credentials:
 *
 *   export STAGE = CODE
 *   export Config = { "clientId": "******", "clientSecret": "*****"}
 */
object Cli {
  def main(args: Array[String]): Unit = {
    program(NextInvoiceDateInput("A-S00000000"))
  }
}
