package com.gu.invoicing.invoice

import com.gu.invoicing.common.ZuoraAuth.{accessToken, zuoraApiHost}
import com.gu.invoicing.invoice.Model._
import com.gu.invoicing.common.Http
import scala.util.chaining._
import com.gu.spy._

/** Zuora API client and implementation details
  */
object Impl {
  private def stripZoqlMargins(str: String): String =
    str.stripMargin.linesIterator.map(_.trim).mkString(" ").trim

  def getAccountIds(identityId: String) = {
    Http(s"$zuoraApiHost/v1/action/query")
      .header("Authorization", s"Bearer ${accessToken}")
      .header("Content-Type", "application/json")
      .postData(
        s"""{
           |  "queryString":
           |    "select Id
           |    from Account
           |    where IdentityId__c = '$identityId'"
           |}""".pipe(stripZoqlMargins)
      )
      .method("POST")
      .asString
      .body
      .pipe(read[Accounts](_))
      .records
      .map(_.Id)
  }

  def getInvoices(account: String): List[Invoice] = {
    Http(s"$zuoraApiHost/v1/transactions/invoices/accounts/$account")
      .header("Authorization", s"Bearer ${accessToken}")
      .asString
      .body
      .pipe(read[Invoices](_))
      .invoices
      .iterator
      .filter { _.amount >= 0.0 }
      .filter { _.status == "Posted" }
      .toList
  }

  def getPayments(account: String): List[Payment] = {
    Http(s"$zuoraApiHost/v1/transactions/payments/accounts/$account")
      .header("Authorization", s"Bearer ${accessToken}")
      .asString
      .body
      .pipe(read[Payments](_))
      .payments
      .filter(_.paidInvoices.nonEmpty)
  }

  def getPaymentMethods(accountId: String): List[PaymentMethod] = {
    Http(s"$zuoraApiHost/v1/action/query")
      .header("Authorization", s"Bearer ${accessToken}")
      .header("Content-Type", "application/json")
      .postData(s"""{
           |  "queryString":
           |    "select
           |      Id,
           |      Type,
           |      AccountId,
           |      BankCode,
           |      BankTransferType,
           |      BankTransferAccountName,
           |      BankTransferAccountNumberMask,
           |      PaypalEmail,
           |      CreditCardMaskNumber,
           |      CreditCardExpirationMonth,
           |      CreditCardExpirationYear,
           |      CreditCardType
           |    from PaymentMethod
           |    where AccountId = '$accountId'"
           |}""".pipe(stripZoqlMargins))
      .method("POST")
      .asString
      .body
      .pipe(read[PaymentMethods](_))
      .records
  }

  def getPaymentMethod(
      invoiceId: String,
      invoices: List[Invoice],
      payments: List[Payment],
      paymentMethods: List[PaymentMethod]
  ): Option[PaymentMethod] = {
    val paymentMethodIdByInvoiceId: Map[String, String] =
      payments.flatMap { payment =>
        payment.paidInvoices
          .tap(v =>
            assert(
              v.length == 1,
              s"Payment should be associated with only one invoice: ${payment.spy}"
            )
          )
          .headOption
          .map { _.invoiceId -> payment.paymentMethodId }
      }.toMap

    val paymentMethodById: Map[String, PaymentMethod] =
      paymentMethods.map(paymentMethod => paymentMethod.Id -> paymentMethod).toMap

    val paymentMethodByInvoiceId: Map[String, Option[PaymentMethod]] =
      invoices.map { invoice =>
        paymentMethodIdByInvoiceId
          .get(invoiceId) match { // invoice might not be (yet) associated with payment
          case Some(paymentMethodId) => invoice.id -> paymentMethodById.get(paymentMethodId)
          case None                  => invoice.id -> None
        }
      }.toMap

    paymentMethodByInvoiceId(invoiceId)
  }

  def time[R](block: => R): R = {
    val t0 = System.nanoTime()
    val result = block // call-by-name
    val t1 = System.nanoTime()
    println("Elapsed time: " + (t1 - t0) / 1_000_000_000d + "s")
    result
  }

  def joinInvoicesWithPayment(
      invoices: List[Invoice],
      payments: List[Payment],
      paymentMethods: List[PaymentMethod]
  ): List[InvoiceWithPayment] = {
    invoices.flatMap { invoice => // filter out invoices with no associated payment methods
      getPaymentMethod(invoice.id, invoices, payments, paymentMethods)
        .map { InvoiceWithPayment(invoice, _) }
    }
  }

  /** The following two methods handle the rare edge case of user being invoiced for multiple
    * subscriptions in a single invoice. Such invoices have a single price, however MMA design
    * displays invoices grouped by product. This happens in the following cases:
    *   - customer has been through a conversation with a CSR on the phone who would have explained
    *     that there would be a product transition in their next bill; or
    *   - it's a result of some kind of manual account for a special reason (e.g. GW agent)
    *
    * This scenario can be simulated/tested by
    *   1. find existing account in Zuora 2. create multiple subscriptions via 'create new
    *      subscription' button with same contract/acceptance date 3. bill run with acceptance date
    *      4. post invoices 5. process payment
    */
  def supportInvoicesWithMultipleSubscriptions(invoices: List[Invoice]): List[Invoice] = {
    invoices.flatMap { invoice =>
      invoice.invoiceItems
        .groupBy(_.subscriptionName)
        .keys
        .toList
        .map { subName => invoice.copy(invoiceItems = List(InvoiceItem(subName))) }
    }
  }
  def tagMultiSubInvoices(invoices: List[MmaInvoiceWithPayment]): List[MmaInvoiceWithPayment] = {
    invoices
      .groupBy(_.invoiceId)
      .flatMap { case (id, invoices) =>
        invoices.map(_.copy(hasMultipleSubs = invoices.length > 1))
      }
      .toList
  }

  def transformToMmaExpectedFormat(
      invoicesWithPayment: List[InvoiceWithPayment]
  ): List[MmaInvoiceWithPayment] = {
    invoicesWithPayment.map(MmaInvoiceWithPayment.apply)
  }
}
