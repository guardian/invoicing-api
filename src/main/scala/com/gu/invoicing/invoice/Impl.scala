package com.gu.invoicing.invoice

import java.lang.System.getenv
import java.time.LocalDate
import com.gu.invoicing.invoice.Model._
import scalaj.http.Http
import scala.util.chaining._
import com.gu.spy._

/**
 * Zuora API client and implementation details
 */
object Impl {
  lazy val stage = getenv("Stage")

  lazy val zuoraApiHost: String =
    stage match { case "DEV" | "CODE" => "https://rest.apisandbox.zuora.com"; case "PROD" => "https://rest.zuora.com" }

  lazy val config = read[Config](getenv("Config"))

  lazy val accessToken: String = {
    Http(s"$zuoraApiHost/oauth/token")
      .postForm(Seq(
        "client_id" -> config.clientId,
        "client_secret" -> config.clientSecret,
        "grant_type" -> "client_credentials"
      ))
      .asString
      .body
      .pipe(read[AccessToken](_))
      .access_token
  }

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
      .postData(
        s"""{
           |  "queryString":
           |    "select
           |      Id,
           |      Type,
           |      AccountId,
           |      BankCode,
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
      payments
        .flatMap { payment =>
          payment
            .paidInvoices
            .tap(v => assert(v.length == 1, s"Payment should be associated with only one invoice: ${payment.spy}"))
            .headOption
            .map { _.invoiceId -> payment.paymentMethodId }
        }.toMap

    val paymentMethodById: Map[String, PaymentMethod] =
      paymentMethods.map(paymentMethod => paymentMethod.Id -> paymentMethod).toMap

    val paymentMethodByInvoiceId: Map[String, Option[PaymentMethod]] =
      invoices.map { invoice =>
        paymentMethodIdByInvoiceId.get(invoiceId) match { // invoice might not be (yet) associated with payment
          case Some(paymentMethodId) => invoice.id -> paymentMethodById.get(paymentMethodId)
          case None => invoice.id -> None
        }
      }.toMap

    paymentMethodByInvoiceId(invoiceId)
  }

  def time[R](block: => R): R = {
    val t0 = System.nanoTime()
    val result = block    // call-by-name
    val t1 = System.nanoTime()
    println("Elapsed time: " + (t1 - t0) / 1_000_000_000d + "s")
    result
  }

  def joinInvoicesWithPayment(
    invoices: List[Invoice],
    payments: List[Payment],
    paymentMethods: List[PaymentMethod],
  ): List[InvoiceWithPayment] = {
    invoices.flatMap { invoice => // filter out invoices with no associated payment methods
      getPaymentMethod(invoice.id, invoices, payments, paymentMethods)
        .map { InvoiceWithPayment(invoice, _) }
    }
  }

  /**
   * Currently we can handle invoices that have only one Subscriptions, so make sure
   * all the invoice items have the same subscriptionId field, and return on of them
   */
  def allInvoicesShouldHaveASingleSubscription(invoices: List[Invoice]): Unit = {
    invoices.foreach { invoice =>
      assert(
        invoice.invoiceItems.groupBy(_.subscriptionName).keys.size == 1,
        s"There should be only a single subscription per invoice: $invoices"
      )
    }
  }

  def transformToMmaExpectedFormat(
    invoicesWithPayment: List[InvoiceWithPayment]
  ): List[MmaInvoiceWithPayment] = {
    invoicesWithPayment.map(MmaInvoiceWithPayment.apply)
  }
}
