package com.gu.invoicing.pdf

import com.gu.invoicing.pdf.Model._
import com.gu.invoicing.pdf.Impl._
import com.gu.invoicing.common.Retry._

import java.lang.System.getenv

object Program {

  /** Main business logic */
  def program(input: PdfInput): String = retryUnsafe {
    val PdfInput(invoiceId, identityId) = input
    val invoice = getInvoice(invoiceId)
    val account = getAccount(invoice.AccountId)
    assert(
      identityId == account.basicInfo.IdentityId__c,
      s"Requested invoice id: $invoiceId appears to belong to different identity: ${account.basicInfo.IdentityId__c}",
    )
    invoice.Body
  }
}
