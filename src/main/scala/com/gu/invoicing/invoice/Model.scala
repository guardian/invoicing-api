package com.gu.invoicing.invoice

import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatter.ofPattern
import java.time.{LocalDate, LocalDateTime}
import java.util.UUID
import com.gu.spy._

/**
 * Needed for upickle to handle optional fields
 * http://www.lihaoyi.com/upickle/#CustomConfiguration
 */
class OptionPickler extends upickle.AttributeTagged {
  implicit def optionWriter[T: Writer]: Writer[Option[T]] =
    implicitly[Writer[T]].comap[Option[T]] {
      case None => null.asInstanceOf[T]
      case Some(x) => x
    }

  implicit def optionReader[T: Reader]: Reader[Option[T]] = {
    new Reader.Delegate[Any, Option[T]](implicitly[Reader[T]].map(Some(_))){
      override def visitNull(index: Int) = None
    }
  }
}

/**
 * Data models and JSON codecs
 */
object Model extends OptionPickler {
  case class Config(clientId: String, clientSecret: String)
  case class AccessToken(access_token: String)

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
    invoiceFiles: List[InvoiceFile],
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
  case class InvoiceFile(
    id: String,
    versionNumber: Int,
    pdfFileUrl: String
  )

  case class InvoiceWithPayment(
    subscriptionName: String,
    date: LocalDate,
    paymentMethod: PaymentMethod,
    price: Double,
    pdfFileId: String, // used to download the file
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
      // PDF invoice files are in reverse chronological order meaning most recent version is first
      // https://www.zuora.com/developer/api-reference/#operation/GET_InvoiceFiles
      val pdfFileId =
        invoice
          .invoiceFiles
          .headOption
          .getOrElse(throw new AssertionError(s"PDF file should exist for each invoice: $invoice"))
          .pdfFileUrl match { case s"/v1/files/$fileId" => fileId }

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
        pdfFileId = pdfFileId,
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
    downloadUrl: String,
    price: Double,
    paymentMethod: String,
    last4: Option[String] = None // for card and direct debit
  )

  object MmaInvoiceWithPayment {
    def apply(invoiceWithPayment: InvoiceWithPayment): MmaInvoiceWithPayment = {
      val mmaResponse = new MmaInvoiceWithPayment(
        invoiceId = invoiceWithPayment.invoiceId,
        subscriptionName = invoiceWithPayment.subscriptionName,
        date = invoiceWithPayment.date,
        downloadUrl = invoiceWithPayment.pdfFileId,
        price = invoiceWithPayment.price,
        paymentMethod = invoiceWithPayment.paymentMethod.Type
      )

      val paymentMethod = invoiceWithPayment.paymentMethod
      import paymentMethod._
      paymentMethod.Type match {// ACH, BankTransfer, Cash, Check, CreditCard, CreditCardReferenceTransaction, DebitCard, Other, PayPal, WireTransfer
        case "CreditCard" | "CreditCardReferenceTransaction" | "DebitCard" =>
          mmaResponse.copy(last4 = CreditCardMaskNumber)

        case "BankTransfer" =>
          mmaResponse.copy(last4 = BankTransferAccountNumberMask.map(_.dropWhile(_ == '*')))

        case "PayPal" =>
          mmaResponse.copy(last4 = None)

        case _ =>
          throw new RuntimeException(s"Unexpected payment method: ${paymentMethod.spy}")
      }
    }
  }

  case class InvoicesOutput(
    invoices: List[MmaInvoiceWithPayment]
  )
  case class InvoicesInput(
    accountId: String
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
    LastTransactionDateTime: LocalDateTime,
    NumConsecutiveFailures: Int,
    TotalNumberOfProcessedPayments: Int,
    UpdatedById: String,
    CreatedDate: LocalDateTime,
    UseDefaultRetryRule: Boolean,
    LastTransactionStatus: String,
    PaymentMethodStatus: String,
    UpdatedDate: LocalDateTime,
    CreatedById: String,
    TotalNumberOfErrorPayments: Int,
    Active: Boolean,
    Type: String,

    // Bank Account (Direct Debit)
    BankCode: Option[String] = None,
    BankTransferAccountName: Option[String] = None,
    BankTransferAccountNumberMask: Option[String] = None,

    // PayPal
    PaypalEmail: Option[String] = None,

    // Credit Card
    CreditCardMaskNumber: Option[String] = None,
    CreditCardExpirationMonth: Option[String] = None,
    CreditCardExpirationYear: Option[String] = None,
    CreditCardType: Option[String] = None,
   )

  case class PaymentMethods(
    records: List[PaymentMethod],
    done: Boolean,
    size: Int
  )

  implicit val bigDecimalRW: ReadWriter[BigDecimal] = readwriter[Double].bimap[BigDecimal](_.toDouble, double => BigDecimal(double.toString))
  implicit val localDateRW: ReadWriter[LocalDate] = readwriter[String].bimap[LocalDate](_.toString, LocalDate.parse(_, ofPattern("yyyy-MM-dd")))
  implicit val localDateTimeRW: ReadWriter[LocalDateTime] = readwriter[String].bimap[LocalDateTime](_.toString, LocalDateTime.parse(_, DateTimeFormatter.ISO_OFFSET_DATE_TIME)) // ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

  implicit val configRW: ReadWriter[Config] = macroRW
  implicit val accessTokenRW: ReadWriter[AccessToken] = macroRW

  implicit val invoicesRW: ReadWriter[Invoices] = macroRW
  implicit val invoiceRW: ReadWriter[Invoice] = macroRW
  implicit val invoiceItemRW: ReadWriter[InvoiceItem] = macroRW
  implicit val invoiceFileRW: ReadWriter[InvoiceFile] = macroRW

  implicit val paymentRW: ReadWriter[Payment] = macroRW
  implicit val paidInvoiceRW: ReadWriter[PaidInvoice] = macroRW
  implicit val paymentsRW: ReadWriter[Payments] = macroRW

  implicit val paymentMethodRW: ReadWriter[PaymentMethod] = macroRW
  implicit val paymentMethodsRW: ReadWriter[PaymentMethods] = macroRW

  implicit val invoiceWithPaymentRW: ReadWriter[InvoiceWithPayment] = macroRW
  implicit val mmaInvoiceWithPaymentRW: ReadWriter[MmaInvoiceWithPayment] = macroRW

  // https://docs.aws.amazon.com/apigateway/latest/developerguide/set-up-lambda-proxy-integrations.html#api-gateway-simple-proxy-for-lambda-input-format
  case class AccountId(accountId: String)
  case class ApiGatewayInput(pathParameters: AccountId)

  // https://aws.amazon.com/premiumsupport/knowledge-center/malformed-502-api-gateway/
  case class ApiGatewayOutput(statusCode: Int, body: String)

  implicit val AccountIdRW: ReadWriter[AccountId] = macroRW
  implicit val awsBodyRW: ReadWriter[ApiGatewayInput] = macroRW
  implicit val apiGatewayOutputRW: ReadWriter[ApiGatewayOutput] = macroRW
  implicit val invoicesInputRW: ReadWriter[InvoicesInput] = macroRW
  implicit val invoicesOutputRW: ReadWriter[InvoicesOutput] = macroRW
}
