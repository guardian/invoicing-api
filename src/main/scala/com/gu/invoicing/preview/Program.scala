package com.gu.invoicing.preview

import com.gu.invoicing.preview.Model._
import com.gu.invoicing.preview.Impl._
import com.gu.invoicing.common.Retry._
import scala.util.chaining._
import pprint._

/**
 * Split (paper) invoice items in any date range into higher granularity publications.
 *
 * Implementation has to account for
 *   - billing-preview not returning past invoice items
 *   - billing-preview not returning price with tax
 *   - any active percentage discounts
 */
object Program { /** Main business logic */
  def program(input: PreviewInput): PreviewOutput = retryUnsafe {
    val PreviewInput(subscriptionName, start, end) = input
    val accountId             = getAccountId(subscriptionName)
    val allRatePlanCharges    = getRatePlanCharges(subscriptionName, start)
    val paidRatePlanCharges   = allRatePlanCharges.filter(_.price > 0.0)
    val pastInvoiceItems      = getPastInvoiceItems(accountId, subscriptionName, start, end)
    val futureInvoiceItems    = getFutureInvoiceItems(accountId, subscriptionName, end)
    val pastItemsWithTax      = pastInvoiceItems.map(addTaxToPastInvoiceItems)
    val futureItemsWithTax    = futureInvoiceItems.map(addTaxToFutureInvoiceItems(_, paidRatePlanCharges))
    val allItemsWithTax       = pastItemsWithTax ++ futureItemsWithTax
    val invoiceItems          = collectRelevantInvoiceItems(subscriptionName, allItemsWithTax)
    val nextInvoiceDate       = findNextInvoiceDate(invoiceItems)
    val publications          = invoiceItems.flatMap(splitInvoiceItemIntoPublications)
    val affectedPublications  = findAffectedPublicationsWithRange(publications, start, end)
    val discountedPubs        = affectedPublications.map(applyAnyDiscounts(allRatePlanCharges, _))
    PreviewOutput(subscriptionName, nextInvoiceDate, start, end, discountedPubs)
  }
}
