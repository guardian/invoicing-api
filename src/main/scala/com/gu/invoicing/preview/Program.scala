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
    val pastInvoiceItems      = getPastInvoiceItems(accountId, subscriptionName, start).map(addTaxToPastInvoiceItems)
    val futureInvoiceItems    = getFutureInvoiceItems(accountId, start).map(addTaxToFutureInvoiceItems(_, ratePlanCharges))
    val pastItemsWithTax      = pastInvoiceItems.map(addTaxToPastInvoiceItems)
    val futureItemsWithTax    = futureInvoiceItems.map(addTaxToFutureInvoiceItems(_, ratePlanCharges))
    val allItemsWithTax       = pastItemsWithTax ++ futureItemsWithTax
    val invoiceItems          = collectRelevantInvoiceItems(subscriptionName, allItemsWithTax)
    val nextInvoiceDate       = findNextInvoiceDate(invoiceItems)
    val publications          = invoiceItems.flatMap(splitInvoiceItemIntoPublications)
    val affectedPublications  = findAffectedPublicationsWithRange(publications, start, end)
    PreviewOutput(subscriptionName, nextInvoiceDate, start, end, affectedPublications)
  }
}
