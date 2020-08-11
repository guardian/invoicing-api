package com.gu.invoicing.pdf

import com.gu.invoicing.invoice.Log._
import com.gu.invoicing.invoice.Model._
import scala.util.chaining._
import Impl._
import Program._
import com.gu.invoicing.pdf.Model.PdfInput
import com.gu.spy._

/**
 * Create environmental variables with Zuora OAuth credentials:
 *
 *   export STAGE = CODE
 *   export Config = { "clientId": "******", "clientSecret": "*****"}
 */
object Cli {
  def main(args: Array[String]): Unit = {
    program(PdfInput("anInvoiceId", "anIdentityId"))
  }
}
