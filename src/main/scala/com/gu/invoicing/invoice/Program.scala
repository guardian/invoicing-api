package com.gu.invoicing.invoice

import com.gu.invoicing.invoice.Model._
import scala.async.Async.{async, await}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.chaining._
import Impl._
import com.gu.spy._

object Program { /** Main business logic */
  def program(input: InvoicesInput): Future[InvoicesOutput] = async {
    val InvoicesInput(accountId) = input

    val invoicesF              = async(getInvoices(accountId))
    val paymentsF              = async(getPayments(accountId))
    val paymentMethodsF        = async(getPaymentMethods(accountId))
    val positiveInvoices       = await(invoicesF)
    val payments               = await(paymentsF)
    val paymentMethods         = await(paymentMethodsF)
    val _                      = allInvoicesShouldHaveASingleSubscription(positiveInvoices)
    val invoicesWithPayment    = joinInvoicesWithPayment(positiveInvoices, payments, paymentMethods)
    val mmaInvoicesWithPayment = transformToMmaExpectedFormat(invoicesWithPayment)

    InvoicesOutput(mmaInvoicesWithPayment)
  }
}
