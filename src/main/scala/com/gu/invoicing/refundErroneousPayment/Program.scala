package com.gu.invoicing.refundErroneousPayment

import com.gu.invoicing.refundErroneousPayment.Impl._
import com.gu.invoicing.refundErroneousPayment.Model._

object Program {

  def program(input: RefundInput): RefundOutput = {
    val RefundInput(accountId, paymentDate, comment, _) = input
    val payments = getPayments(accountId, paymentDate)
    assert(payments.nonEmpty, s"No payments were taken on $paymentDate")
    val invoices = getInvoices(accountId)
    assert(invoices.nonEmpty, "No invoices for this account")
    val dateOfLatestInvoice = invoices.map(_.InvoiceDate).max
    val invoicesOnLatestDate = invoices.filter(_.InvoiceDate == dateOfLatestInvoice)
    val balancingInvoice = invoicesOnLatestDate.minBy(_.Amount)
    assert(
      balancingInvoice.InvoiceDate.isBefore(paymentDate),
      "Balancing invoice should have been created before the erroneous payment was taken"
    )
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
    payment.paidInvoices match {
      case paidInvoice :: Nil =>
        val refundId = createRefundObject(payment.amount, payment.id, comment)
        assert(getRefundStatus(refundId) == "Processed", "Refund hasn't been processed")
        applyCreditBalance(paidInvoice.invoiceNumber, payment.amount, comment)
        RefundData(paidInvoice.invoiceNumber, payment.amount, payment.id, refundId)
      case _ =>
        throw new AssertionError("assertion failed: Payment is for multiple invoices")
    }
  }
}
