package com.gu.invoicing.preview

import java.time.LocalDate

import scala.util.chaining._
import Impl._
import Program._
import com.gu.invoicing.preview.Model._
import com.gu.spy._
import pprint._

/** Create environmental variables with Zuora OAuth credentials:
  *
  * export STAGE = CODE export Config = { "clientId": "******", "clientSecret": "*****"}
  */
object Cli {
  def main(args: Array[String]): Unit = {
    val input = """A-S0000000?startDate=2021-02-14&endDate=2021-02-15"""
    input match {
      case s"$sub?startDate=$startDate&endDate=$endDate" =>
        program(PreviewInput(sub, LocalDate.parse(startDate), LocalDate.parse(endDate))) tap (log(
          _
        ))
    }
  }
}
