package com.gu.invoicing.preview

import java.time.LocalDate

import scala.util.chaining._
import Impl._
import Program._
import com.gu.invoicing.preview.Model._
import com.gu.spy._

/**
 * Create environmental variables with Zuora OAuth credentials:
 *
 *   export STAGE = CODE
 *   export Config = { "clientId": "******", "clientSecret": "*****"}
 */
object Cli {
  def main(args: Array[String]): Unit = {
    program(PreviewInput("A-S00000000", LocalDate.parse("2020-11-13"), LocalDate.parse("2020-12-25")))
  }
}
