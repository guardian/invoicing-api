package com.gu.invoicing.invoice

import com.gu.invoicing.invoice.Model._
import scala.async.Async.{async, await}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.chaining._
import Impl._
import com.gu.spy._
import com.gu.invoicing.common.Retry._

object Program {

  /** Main business logic */
  /** Single identity can have multiple subscriptions where each subscription belongs to a different account, hence we
    * first retrieve all accountIds belonging to identityId, and then all invoices belonging to each accountId. This
    * represents Guardian model on top of Zuora model.
    */
  def program(input: InvoicesInput): Future[InvoicesOutput] = retry {
    Future
      .traverse(getAccountIds(input.identityId))(invoicesByAccountId)
      .map(v => InvoicesOutput(v.flatten))
  }

  // This directly maps to Zuora model where invoices belong to a single account.
  private def invoicesByAccountId(accountId: String): Future[List[MmaInvoiceWithPayment]] = async {
    val invoicesF = async(getInvoices(accountId))
    val paymentsF = async(getPayments(accountId))
    val paymentMethodsF = async(getPaymentMethods(accountId))
    val positiveInvoices = await(invoicesF)
    val payments = await(paymentsF)
    val paymentMethods = await(paymentMethodsF)
    val invoices = supportInvoicesWithMultipleSubscriptions(positiveInvoices)
    val invoicesWithPayment = joinInvoicesWithPayment(invoices, payments, paymentMethods)
    val mmaInvoicesWithPayment = transformToMmaExpectedFormat(invoicesWithPayment)
    val multiSubTaggedInvoices = tagMultiSubInvoices(mmaInvoicesWithPayment)
    multiSubTaggedInvoices
  }
}
