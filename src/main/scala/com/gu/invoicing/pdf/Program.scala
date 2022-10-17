package com.gu.invoicing.pdf

import com.gu.invoicing.pdf.Model._
import com.gu.invoicing.pdf.Impl._
import com.gu.invoicing.common.Retry._

import java.util.Currency

object Program {

  private val GNMAustralia_InvoiceTemplateID = "2c92a0fd5ecce80c015ee71028643020" // GNM Australia Pty Ltd

  /** Main business logic */
  def program(input: PdfInput): String = retryUnsafe {
    val PdfInput(invoiceId, identityId) = input
    val invoice = getInvoice(invoiceId)
    assert(
      invoiceId == invoice.Id,
      s"Returned invoice id: ${invoice.Id} does not match requested invoice id: $invoiceId"
    )
    val account = getAccount(invoice.AccountId)
    assert(
      identityId == account.basicInfo.IdentityId__c,
      s"Requested invoice id: $invoiceId appears to belong to different identity: ${account.basicInfo.IdentityId__c}"
    )
    if (repairRequired(account)) {
      updateInvoiceTemplateId(invoice.AccountId, GNMAustralia_InvoiceTemplateID)
      regenerateInvoice(invoice.Id)
      getInvoice(invoice.Id).Body
    } else {
      invoice.Body
    }
  }

  // If the sold to country is Australia, the currency is AUD but the invoice template ID is not the correct one,
  // then repair things by setting the correct invoice template ID, regenerating the PDF, and re-getting the PDF body.
  private def repairRequired(account: Account): Boolean = {
    account.soldToContact.country == "Australia" &&
    account.billingAndPayment.currency == "AUD" &&
      account.basicInfo.invoiceTemplateId != GNMAustralia_InvoiceTemplateID
  }

}
