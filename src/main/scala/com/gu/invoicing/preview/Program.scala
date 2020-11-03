package com.gu.invoicing.preview

import java.time.LocalDate
import com.gu.invoicing.preview.Model._
import com.gu.invoicing.preview.Impl._
import com.gu.invoicing.common.Retry._
import scala.util.chaining._
import pprint._

/**
 * End of Last Invoice Period - the day after the last invoiced service period.
 * If service period is 2020-09-27 to 2020-10-26, then end of last invoice period, or
 * equivalently next invoice date, is 2020-10-27. Note that serviceEndDate is inclusive
 * unlike misnomer chargedThroughDate which is exclusive. The reason why we cannot
 * always rely on chargedThroughDate is because we do not perform bill run in real-time
 * at point of acquisition.
 */
object Program { /** Main business logic */
  def program(input: PreviewInput): PreviewOutput = retryUnsafe {
    val PreviewInput(subscriptionName, start, end) = input
    val accountId             = getAccountId(subscriptionName)
    val pastInvoiceItems      = getPastInvoiceItems(accountId, subscriptionName, start)
    val futureInvoiceItems    = getFutureInvoiceItems(accountId, start)
    val allInvoiceItems       = pastInvoiceItems ++ futureInvoiceItems
    val invoiceItems          = collectRelevantInvoiceItems(subscriptionName, allInvoiceItems)
    val nextInvoiceDate       = findNextInvoiceDate(invoiceItems)
    val publications          = invoiceItems.flatMap(splitInvoiceItemIntoPublications)
    val affectedPublications  = findAffectedPublicationsWithRange(publications, start, end)
    PreviewOutput(subscriptionName, nextInvoiceDate, start, end, affectedPublications) tap (log(_))
  }
}
