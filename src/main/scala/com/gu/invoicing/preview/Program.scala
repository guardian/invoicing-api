package com.gu.invoicing.preview

import com.gu.invoicing.preview.Model._
import com.gu.invoicing.preview.Impl._
import com.gu.invoicing.common.Retry._
import scala.util.chaining._
import pprint._

/**
 * Split (paper) invoice items in any date range into higher granularity publications.
 */
object Program { /** Main business logic */
  def program(input: PreviewInput): PreviewOutput = retryUnsafe {
    val PreviewInput(subscriptionName, start, end) = input
    val accountId             = getAccountId(subscriptionName)
    val ratePlanCharges       = getRatePlanCharges(subscriptionName, start)
    val pastInvoiceItems      = getPastInvoiceItems(accountId, subscriptionName, start)
    val futureInvoiceItems    = getFutureInvoiceItems(accountId, start)
    val allInvoiceItems       = pastInvoiceItems ++ futureInvoiceItems
    val invoiceItems          = collectRelevantInvoiceItems(subscriptionName, allInvoiceItems)
    val itemsWithTax          = invoiceItems.map(addTax(_, ratePlanCharges))
    val nextInvoiceDate       = findNextInvoiceDate(itemsWithTax)
    val publications          = itemsWithTax.flatMap(splitInvoiceItemIntoPublications)
    val affectedPublications  = findAffectedPublicationsWithRange(publications, start, end)
    PreviewOutput(subscriptionName, nextInvoiceDate, start, end, affectedPublications)
  }
}
