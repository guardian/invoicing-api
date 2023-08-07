package com.gu.invoicing.refund

import Model._
import Impl._
import com.gu.invoicing.common.Assert._
import scala.util.chaining._

/** Main business logic. Can be executed as CLI application or AWS Lambda
  *
  * Zuora cannot apply refund in one go. It is applied by first creating a refund object, and then spreading the total
  * refund amount over possibly multiple invoice items by applying invoice item adjustment to corresponding items.
  * Previous charge/credit adjustments have to be accounted for to calculate available adjustment amount remaining.
  */
object Program {
  def program(input: RefundInput): RefundOutput = {
    System.out.println("Testing deploy")
    val RefundInput(subscriptionName, refund, adjustInvoices, guid, _) = input

    val subscription = getSubscription(subscriptionName) tap { subscription =>
      s"$subscriptionName should be retrieved" assert (subscription.subscriptionNumber == subscriptionName)
    }
    System.out.println(s"Sub was retrieved successfully: $subscription")
    val balanceBeforeRefund = getAccountBalance(subscription.accountId) tap { balanceBeforeRefund =>
      s"$balanceBeforeRefund should be recorded" assert true
    }
    System.out.println("Balance is recorded")
    val invoices = getInvoices(subscription.accountId) tap { invoices =>
      s"$subscriptionName should have at least one invoice" assert invoices.nonEmpty
    }
    System.out.println("Sub has at least one invoice")
    val itemsByInvoiceId = getItemsByInvoice(subscriptionName) tap { itemsByInvoiceId =>
      s"$subscriptionName should have at least one invoice item" assert itemsByInvoiceId.nonEmpty
    }
    System.out.println("Successfully got InvoiceItems")

    val (invoiceId, invoice, items) = decideRelevantInvoice(invoices, itemsByInvoiceId) tap { case (_, invoice, _) =>
      s"$invoice should be at least posted and not negative" assert (invoice.Amount > 0.0 && invoice.Status == "Posted")
    }
    System.out.println(s"Invoice $invoice is posted and not negative")

    val itemAdjustments = getInvoiceItemAdjustments(invoiceId) tap { _ =>
      s"Invoice items of $invoiceId should be retrieved" assert true
    }
    System.out.println("Successfully got InvoiceItemAdjustments")

    val Some(paymentId) = getInvoicePaymentId(invoiceId) tap { paymentId =>
      s"$invoiceId should have a payment" assert paymentId.isDefined
    }
    System.out.println(s"Invoice with id $invoiceId has a payment")

    val taxationItems =
      if (invoiceHasTaxationItems(items)) {
        System.out.println(s"Invoice has taxable items, fetching taxation item ids")
        getTaxationItemsForInvoice(invoiceId)
      } else {
        System.out.println(s"No taxable invoice items")
        Nil
      }

    val adjustmentsUnrounded = spreadRefundAcrossItems(items, taxationItems, itemAdjustments, refund, guid) tap {
      adjustmentsUnrounded =>
        s"$adjustmentsUnrounded (un-rounded) should equal total $refund" assert (adjustmentsUnrounded
          .map(_.Amount)
          .sum == refund)
    }
    val adjustmentsRounded = roundAdjustments(adjustmentsUnrounded) tap { adjustmentsRounded =>
      s"$adjustmentsRounded (rounded) amount should equal total $refund" assert (adjustmentsRounded
        .map(_.Amount)
        .sum == refund)
    }
    System.out.println(s"Adjustment totals are correct")

    val refundId = createRefundObject(refund, paymentId, guid) tap { _ =>
      s"Refund object for $paymentId should be created" assert true
    }
    System.out.println(s"Refund with id $refundId created successfully")

    getRefundStatus(refundId) tap { refundStatus =>
      s"$refundId in amount of $refund should be processed" assert (refundStatus == "Processed")
    }
    System.out.println(s"Refund processed successfully")

    if (adjustInvoices) {
      System.out.println(s"Attempting to adjust invoices")
      applyRefundOverItemAdjustments(adjustmentsRounded) tap { adjustments =>
        s"All $adjustments should be successful" assert adjustments.forall(_.Success)
      }
      System.out.println(s"Checking account balance has not changed")
      getAccountBalance(subscription.accountId) tap { balanceAfterRefund =>
        s"${subscription.accountId} balance should not change after refund" assert (balanceBeforeRefund == balanceAfterRefund)
      }
      System.out.println(s"Invoices adjusted successfully")
    } else {
      System.out.println(s"adjustInvoices was false")
    }

    RefundOutput(subscriptionName, refund, invoiceId, paymentId, adjustmentsRounded, guid)
  }
}
