package com.gu.invoicing.pdf

import com.gu.invoicing.pdf.Model._
import com.gu.invoicing.pdf.Impl._

object Program { /** Main business logic */
  def program(input: PdfInput): String = {
    val PdfInput(invoiceId, identityId) = input
    val Invoice(accountId, pdf) = getInvoice(invoiceId)
    val actualIdentityId = getIdentityId(accountId)
    assert(identityId == actualIdentityId, s"Requested invoice should NOT belong to different identity $actualIdentityId")
    pdf
  }
}
