package com.gu.invoicing.refundErroneousPayment

import com.gu.invoicing.refundErroneousPayment.Impl._
import com.gu.invoicing.refundErroneousPayment.Model._

import java.time.LocalDate

object Program {

  def program(input: RefundInput): RefundOutput = {
    val RefundInput(accountId, paymentDate, comment, _) = input
    val payments = getPayments(accountId, paymentDate)
    assert(payments.nonEmpty, s"No payments were taken on $paymentDate")
    val invoices = getInvoices(accountId)
    assert(invoices.nonEmpty, "No invoices for this account")
    val balancingInvoice = invoices.maxBy(_.InvoiceDate)
    assert(balancingInvoice.InvoiceDate.isBefore(paymentDate), "Bad balancing invoice date")
    assert(
      balancingInvoice.Amount == -payments.map(_.amount).sum,
      "Payments don't agree with balancing invoice"
    )
    transferToCreditBalance(balancingInvoice.InvoiceNumber, -balancingInvoice.Amount, comment)
    val results = payments.map(processRefund(comment))
    assert(getAccountBalance(accountId) == 0, "Account balance isn't settled")
    RefundOutput(accountId, results, balancingInvoice.InvoiceNumber)
  }

  def processRefund(comment: String)(payment: Payment): RefundData = {
    assert(payment.paidInvoices.length == 1, "Payment is for multiple invoices")
    val refundId = createRefundObject(payment.amount, payment.id, comment)
    assert(getRefundStatus(refundId) == "Processed", "Refund hasn't been processed")
    applyCreditBalance(payment.paidInvoices.head.invoiceNumber, payment.amount, comment)
    RefundData(payment.paidInvoices.head.invoiceNumber, payment.amount, payment.id, refundId)
  }
}
