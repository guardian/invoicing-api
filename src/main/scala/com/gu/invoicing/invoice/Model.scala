package com.gu.invoicing.invoice

import java.time.LocalDate
import com.gu.invoicing.common.JsonSupport
import com.gu.spy._

object Model extends JsonSupport {
  case class Invoices(
      invoices: List[Invoice],
      success: Boolean,
  )
  case class Invoice(
      id: String,
      invoiceNumber: String,
      invoiceDate: LocalDate,
      amount: BigDecimal,
      status: String,
      invoiceItems: List[InvoiceItem],
  )
  case class InvoiceItem(
      subscriptionName: String,
  )
  case class InvoiceWithPayment(
      subscriptionName: String,
      date: LocalDate,
      paymentMethod: PaymentMethod,
      price: Double,
      pdfPath: String, /* invoices/{fileId} */
      invoiceId: String,
  )

  /** Subscription name is not available from the top level Invoice object because Invoice can be associated with
    * multiple subscriptions, however current MMA design wants to group invoices per subscription.
    */
  object InvoiceWithPayment {
    def apply(
        invoice: Invoice,
        paymentMethod: PaymentMethod,
    ): InvoiceWithPayment = {
      // Currently we handle only invoices with single subscription, so any invoice item should do for getting the subscription name
      val subscriptionName =
        invoice.invoiceItems.headOption
          .getOrElse(
            throw new AssertionError(s"At least one invoice item should always exist: $invoice"),
          )
          .subscriptionName

      new InvoiceWithPayment(
        subscriptionName = subscriptionName,
        date = invoice.invoiceDate,
        paymentMethod = paymentMethod,
        price = invoice.amount.toDouble,
        pdfPath = s"invoices/${invoice.id}",
        invoiceId = invoice.id,
      )
    }
  }

  /** This is the model expected by manage-frontend. I kept is as separate concept from InvoiceWithPayment as it is
    * likely to keep changing in the initial stages.
    */
  case class MmaInvoiceWithPayment(
      invoiceId: String,
      subscriptionName: String,
      date: LocalDate,
      pdfPath: String,
      price: Double,
      paymentMethod: String,
      last4: Option[String] = None, // for card and direct debit
      cardType: Option[String] = None, // Visa, MasterCard
      hasMultipleSubs: Boolean, // to handle edge case of multiple subscriptions within a single invoice
  )

  object MmaInvoiceWithPayment {
    def apply(invoiceWithPayment: InvoiceWithPayment): MmaInvoiceWithPayment = {
      val mmaResponse = new MmaInvoiceWithPayment(
        invoiceId = invoiceWithPayment.invoiceId,
        subscriptionName = invoiceWithPayment.subscriptionName,
        date = invoiceWithPayment.date,
        pdfPath = invoiceWithPayment.pdfPath,
        price = invoiceWithPayment.price,
        paymentMethod = invoiceWithPayment.paymentMethod.Type,
        hasMultipleSubs = false,
      )

      val paymentMethod = invoiceWithPayment.paymentMethod
      import paymentMethod._
      paymentMethod.Type match { // ACH, BankTransfer, Cash, Check, CreditCard, CreditCardReferenceTransaction, DebitCard, Other, PayPal, WireTransfer
        case "CreditCard" | "CreditCardReferenceTransaction" | "DebitCard" =>
          mmaResponse.copy(
            last4 = CreditCardMaskNumber.map(dropMaskPrefix),
            cardType = CreditCardType,
            paymentMethod = "Card",
          )
        case "BankTransfer" =>
          mmaResponse.copy(
            last4 = BankTransferAccountNumberMask.map(dropMaskPrefix),
            paymentMethod = paymentMethod.BankTransferType match {
              case Some("SEPA") => "Sepa"
              case _ => "DirectDebit"
            },
          )

        case "PayPal" =>
          mmaResponse.copy(
            last4 = None,
            paymentMethod = "PayPal",
          )

        case _ =>
          throw new RuntimeException(s"Unexpected payment method: ${paymentMethod.spy}")
      }
    }

    private def dropMaskPrefix(s: String): String = s.dropWhile(_ == '*')
  }

  case class InvoicesOutput(
      invoices: List[MmaInvoiceWithPayment],
  )
  case class InvoicesInput(
      identityId: String,
  )
  case class Payment(
      paymentMethodId: String,
      paidInvoices: List[PaidInvoice],
  )
  case class PaidInvoice(
      invoiceId: String,
      invoiceNumber: String,
  )

  case class Payments(
      payments: List[Payment],
      success: Boolean,
  )

  case class PaymentMethod(
      Id: String,
      Type: String, // DebitCard, PayPal
      BankTransferType: Option[String] = None,
      BankTransferAccountNumberMask: Option[String] = None,
      CreditCardMaskNumber: Option[String] = None,
      CreditCardType: Option[String] = None, // Visa, MasterCard
  )

  case class PaymentMethods(
      records: List[PaymentMethod],
      done: Boolean,
      size: Int,
  )

  case class Account(Id: String)
  case class Accounts(
      records: List[Account],
      done: Boolean,
      size: Int,
  )

  implicit val AccountRW: ReadWriter[Account] = macroRW
  implicit val AccountsRW: ReadWriter[Accounts] = macroRW

  implicit val invoicesRW: ReadWriter[Invoices] = macroRW
  implicit val invoiceRW: ReadWriter[Invoice] = macroRW
  implicit val invoiceItemRW: ReadWriter[InvoiceItem] = macroRW

  implicit val paymentRW: ReadWriter[Payment] = macroRW
  implicit val paidInvoiceRW: ReadWriter[PaidInvoice] = macroRW
  implicit val paymentsRW: ReadWriter[Payments] = macroRW

  implicit val paymentMethodRW: ReadWriter[PaymentMethod] = macroRW
  implicit val paymentMethodsRW: ReadWriter[PaymentMethods] = macroRW

  implicit val invoiceWithPaymentRW: ReadWriter[InvoiceWithPayment] = macroRW
  implicit val mmaInvoiceWithPaymentRW: ReadWriter[MmaInvoiceWithPayment] = macroRW

  implicit val invoicesInputRW: ReadWriter[InvoicesInput] = macroRW
  implicit val invoicesOutputRW: ReadWriter[InvoicesOutput] = macroRW
}
