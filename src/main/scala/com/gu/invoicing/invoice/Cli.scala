package com.gu.invoicing.invoice

import com.gu.invoicing.invoice.Log._
import com.gu.invoicing.invoice.Model._
import scala.async.Async.{async, await}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration.Inf
import scala.util.chaining._
import Impl._
import Program._
import com.gu.spy._

/**
 * Create environmental variables with Zuora OAuth credentials:
 *
 *   export STAGE = CODE
 *   export Config = { "clientId": "******", "clientSecret": "*****"}
 */
object Cli {
  def main(args: Array[String]): Unit = {
    (1 to 4).foreach { _ =>
      time { Await.result(program(InvoicesInput("someIdentityId")), Inf) }
    }
  }
}
