package com.gu.invoicing.nextinvoicedate

import com.gu.invoicing.nextinvoicedate.Model._
import com.gu.invoicing.nextinvoicedate.Impl._
import com.gu.invoicing.common.Retry._
import scala.util.chaining._

/** End of Last Invoice Period - the day after the last invoiced service period. If service period
  * is 2020-09-27 to 2020-10-26, then end of last invoice period, or equivalently next invoice date,
  * is 2020-10-27. Note that serviceEndDate is inclusive unlike misnomer chargedThroughDate which is
  * exclusive. The reason why we cannot always rely on chargedThroughDate is because we do not
  * perform bill run in real-time at point of acquisition.
  */

object Program {

  /** Main business logic */
  def program(input: NextInvoiceDateInput): NextInvoiceDateOutput = retryUnsafe {
    val NextInvoiceDateInput(subscriptionName) = input
    val accountId = getAccountId(subscriptionName)
    val allInvoiceItems = getBillingPreview(accountId)
    val invoiceItems = collectRelevantInvoiceItems(subscriptionName, allInvoiceItems)
    val nextInvoiceDate = findNextInvoiceDate(invoiceItems)
    NextInvoiceDateOutput(nextInvoiceDate)
  }
}
