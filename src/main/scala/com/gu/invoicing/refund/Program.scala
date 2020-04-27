package com.gu.invoicing.refund

import Model._
import Impl._
import Assert._
import scala.util.chaining._

/**
 * Main business logic. Can be executed as CLI application or AWS Lambda
 *
 * Zuora cannot apply refund in one go. It is applied by first creating a refund object,
 * and then spreading the total refund amount over possibly multiple invoice items by
 * applying invoice item adjustment to corresponding items. Previous charge/credit adjustments
 * have to be accounted for to calculate available adjustment amount remaining.
 */
object Program {
  def program(input: RefundInput): RefundOutput = {
    val RefundInput(subscriptionName, refund, guid, _) = input

    val subscription          = getSubscription(subscriptionName)                              tap { subscription           => s"$subscriptionName should be retrieved"                                     assert (subscription.subscriptionNumber == subscriptionName) }
    val balanceBeforeRefund   = getAccountBalance(subscription.accountId)                      tap { balanceBeforeRefund    => s"$balanceBeforeRefund should be recorded"                                   assert (true) }
    val invoices              = getInvoices(subscription.accountId)                            tap { invoices               => s"$subscriptionName should have at least one invoice"                        assert (invoices.nonEmpty) }
    val itemsBySubscription   = getItemsByInvoice(subscriptionName)                            tap { invoiceItemsByInvoice  => s"$subscriptionName should have at least one invoice item"                   assert (invoiceItemsByInvoice.nonEmpty) }

    val (invoiceId, _, items) = decideRelevantInvoice(invoices, itemsBySubscription)           tap { case (_, invoice, _)   => s"$invoice should be at least posted and not negative"                       assert (invoice.Amount > 0.0 && invoice.Status == "Posted") }
    val itemAdjustments       = getInvoiceItemAdjustments(invoiceId)                           tap { _                      => s"Invoice items of $invoiceId should be retrieved"                           assert (true) }
    val Some(paymentId)       = getInvoicePaymentId(invoiceId)                                 tap { paymentId              => s"$invoiceId should have a payment"                                          assert (paymentId.isDefined) }
    val adjustmentsUnrounded  = spreadRefundAcrossItems(items, itemAdjustments, refund, guid)  tap { adjustmentsUnrounded   => s"$adjustmentsUnrounded (un-rounded) should equal total $refund"             assert (adjustmentsUnrounded.map(_.Amount).sum == refund) }
    val adjustmentsRounded    = roundAdjustments(adjustmentsUnrounded)                         tap { adjustmentsRounded     => s"$adjustmentsRounded (rounded) amount should equal total $refund"           assert (adjustmentsRounded.map(_.Amount).sum == refund) }

    val refundId              = createRefundObject(refund, paymentId, guid)                    tap { _                      => s"Refund object for $paymentId should be created"                            assert (true) }
    val _                     = getRefundStatus(refundId)                                      tap { refundStatus           => s"$refundId in amount of $refund should be processed"                        assert (refundStatus == "Processed") }
    val result                = applyRefundOverItemAdjustments(adjustmentsRounded)             tap { adjustments            => s"All $adjustments should be successful"                                     assert (adjustments.forall(_.Success)) }
    val balanceAfterRefund    = getAccountBalance(subscription.accountId)                      tap { balanceAfterRefund     => s"${subscription.accountId} balance should not change after refund"          assert (balanceBeforeRefund == balanceAfterRefund) }

    RefundOutput(subscriptionName, refund, invoiceId, paymentId, adjustmentsRounded, guid)
  }
}
