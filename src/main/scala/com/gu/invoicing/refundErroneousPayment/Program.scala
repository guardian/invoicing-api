package com.gu.invoicing.refundErroneousPayment

import com.gu.invoicing.refundErroneousPayment.Impl._
import com.gu.invoicing.refundErroneousPayment.Model._

object Program {

  def program(input: RefundInput): RefundOutput = {
    val RefundInput(accountId, invoiceNumber, paymentId, invoiceAmount, comment, _) = input
    val balancingInvoiceNumber = getBalancingInvoice(accountId, invoiceAmount)
    transferToCreditBalance(balancingInvoiceNumber, invoiceAmount, comment)
    val refundId = createRefundObject(invoiceAmount, paymentId, comment)
    assert(getRefundStatus(refundId) == "Processed")
    applyCreditBalance(invoiceNumber, invoiceAmount, comment)
    assert(getAccountBalance(accountId) == 0)
    RefundOutput(accountId, paymentId, invoiceAmount, invoiceNumber, balancingInvoiceNumber)
  }
}
