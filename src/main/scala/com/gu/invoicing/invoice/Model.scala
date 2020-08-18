package com.gu.invoicing.invoice

import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatter.ofPattern
import java.time.{LocalDate, LocalDateTime}

import com.gu.invoicing.common.JsonSupport
import com.gu.spy._

object Model extends JsonSupport {
  case class Invoices(
    invoices: List[Invoice],
    success: Boolean
  )
  case class Invoice(
    id: String,
    accountId: String,
    accountNumber: String,
    accountName: String,
    invoiceDate: LocalDate,
    invoiceNumber: String,
    dueDate: LocalDate,
    invoiceTargetDate: LocalDate,
    amount: BigDecimal,
    balance: BigDecimal,
    creditBalanceAdjustmentAmount: BigDecimal,
    createdBy: String,
    status: String,
    body: String,
    invoiceItems: List[InvoiceItem],
  )
  case class InvoiceItem(
    id: String,
    subscriptionName: String,
    subscriptionId: String,
    serviceStartDate: LocalDate,
    serviceEndDate: LocalDate,
    chargeAmount: BigDecimal,
    chargeDescription: String,
    chargeName: String,
    chargeId: String,
    productName: String,
    quantity: BigDecimal,
    taxAmount: BigDecimal,
    unitOfMeasure: String,
    chargeDate: String, // FIXME this has different format from other kinds of localdatetimes
    chargeType: String,
    processingType: String,
    appliedToItemId: Option[String]
  )

  case class InvoiceWithPayment(
    subscriptionName: String,
    date: LocalDate,
    paymentMethod: PaymentMethod,
    price: Double,
    pdfPath: String, /* invoices/{fileId} */
    invoiceId: String
  )

  /**
   * Subscription name is not available from the top level Invoice object because Invoice can be associated with
   * multiple subscriptions, however current MMA design wants to group invoices per subscription.
   */
  object InvoiceWithPayment {
    def apply(
      invoice: Invoice,
      paymentMethod: PaymentMethod,
    ): InvoiceWithPayment = {
      // Currently we handle only invoices with single subscription, so any invoice item should do for getting the subscription name
      val subscriptionName =
        invoice
          .invoiceItems
          .headOption
          .getOrElse(throw new AssertionError(s"At least one invoice item should always exist: $invoice"))
          .subscriptionName

      new InvoiceWithPayment(
        subscriptionName = subscriptionName,
        date = invoice.invoiceDate,
        paymentMethod = paymentMethod,
        price = invoice.amount.toDouble,
        pdfPath = s"invoices/${invoice.id}",
        invoiceId = invoice.id
      )
    }
  }

  /**
   * This is the model expected by manage-frontend. I kept is as separate concept from InvoiceWithPayment
   * as it is likely to keep changing in the initial stages.
   */
  case class MmaInvoiceWithPayment(
    invoiceId: String,
    subscriptionName: String,
    date: LocalDate,
    pdfPath: String,
    price: Double,
    paymentMethod: String,
    last4: Option[String] = None, // for card and direct debit
    cardType: Option[String] = None // Visa, MasterCard
  )

  object MmaInvoiceWithPayment {
    def apply(invoiceWithPayment: InvoiceWithPayment): MmaInvoiceWithPayment = {
      val mmaResponse = new MmaInvoiceWithPayment(
        invoiceId = invoiceWithPayment.invoiceId,
        subscriptionName = invoiceWithPayment.subscriptionName,
        date = invoiceWithPayment.date,
        pdfPath = invoiceWithPayment.pdfPath,
        price = invoiceWithPayment.price,
        paymentMethod = invoiceWithPayment.paymentMethod.Type
      )

      val paymentMethod = invoiceWithPayment.paymentMethod
      import paymentMethod._
      paymentMethod.Type match {// ACH, BankTransfer, Cash, Check, CreditCard, CreditCardReferenceTransaction, DebitCard, Other, PayPal, WireTransfer
        case "CreditCard" | "CreditCardReferenceTransaction" | "DebitCard" =>
          mmaResponse.copy(
            last4 = CreditCardMaskNumber.map(dropMaskPrefix),
            cardType = CreditCardType,
            paymentMethod = "Card"
          )
        case "BankTransfer" =>
          mmaResponse.copy(
            last4 = BankTransferAccountNumberMask.map(dropMaskPrefix),
            paymentMethod = "DirectDebit"
          )

        case "PayPal" =>
          mmaResponse.copy(
            last4 = None,
            paymentMethod = "PayPal"
          )

        case _ =>
          throw new RuntimeException(s"Unexpected payment method: ${paymentMethod.spy}")
      }
    }

    private def dropMaskPrefix(s: String): String = s.dropWhile(_ == '*')
  }

  case class InvoicesOutput(
    invoices: List[MmaInvoiceWithPayment]
  )
  case class InvoicesInput(
    identityId: String
  )
  case class Payment(
    id: String,
    accountId: String,
    accountNumber: String,
    accountName: String,
    `type`: String,
    effectiveDate: LocalDate,
    paymentNumber: String,
    paymentMethodId: String,
    amount: BigDecimal,
    paidInvoices: List[PaidInvoice],
    gatewayTransactionNumber: String,
    status: String,
  )

  case class PaidInvoice(
    invoiceId: String,
    invoiceNumber: String,
    appliedPaymentAmount: BigDecimal
  )

  case class Payments(
    payments: List[Payment],
    success: Boolean
  )

  case class PaymentMethod(
    Id: String,
    AccountId: String,
    Type: String, // DebitCard, PayPal
    BankTransferAccountNumberMask: Option[String] = None,
    CreditCardMaskNumber: Option[String] = None,
    CreditCardType: Option[String] = None, // Visa, MasterCard
  )

  case class PaymentMethods(
    records: List[PaymentMethod],
    done: Boolean,
    size: Int
  )

  case class Account(Id: String)
  case class Accounts(
    records: List[Account],
    done: Boolean,
    size: Int
  )

  implicit val bigDecimalRW: ReadWriter[BigDecimal] = readwriter[Double].bimap[BigDecimal](_.toDouble, double => BigDecimal(double.toString))
  implicit val localDateRW: ReadWriter[LocalDate] = readwriter[String].bimap[LocalDate](_.toString, LocalDate.parse(_, ofPattern("yyyy-MM-dd")))
  implicit val localDateTimeRW: ReadWriter[LocalDateTime] = readwriter[String].bimap[LocalDateTime](_.toString, LocalDateTime.parse(_, DateTimeFormatter.ISO_OFFSET_DATE_TIME)) // ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

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

  // https://docs.aws.amazon.com/apigateway/latest/developerguide/set-up-lambda-proxy-integrations.html#api-gateway-simple-proxy-for-lambda-input-format
  case class ApiGatewayInput(headers: Map[String, String])

  // https://aws.amazon.com/premiumsupport/knowledge-center/malformed-502-api-gateway/
  case class ApiGatewayOutput(statusCode: Int, body: String)

  implicit val awsBodyRW: ReadWriter[ApiGatewayInput] = macroRW
  implicit val apiGatewayOutputRW: ReadWriter[ApiGatewayOutput] = macroRW
  implicit val invoicesInputRW: ReadWriter[InvoicesInput] = macroRW
  implicit val invoicesOutputRW: ReadWriter[InvoicesOutput] = macroRW
}
