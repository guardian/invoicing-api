package com.gu.invoicing.refundErroneousPayment

import com.gu.invoicing.common.Http
import com.gu.invoicing.common.ZuoraAuth.{accessToken, zuoraApiHost}
import com.gu.invoicing.refundErroneousPayment.Model._

import java.time.LocalDate
import scala.util.chaining._

/** Zuora API client and implementation details
  */
object Impl {

  def getAccountBalance(accountId: String): BigDecimal = {
    Http(s"$zuoraApiHost/v1/accounts/$accountId")
      .header("Authorization", s"Bearer $accessToken")
      .asString
      .body
      .pipe(read[Account](_))
      .metrics
      .balance
  }

  def createRefundObject(amount: BigDecimal, paymentId: String, comment: String): String = {
    Http(s"$zuoraApiHost/v1/object/refund")
      .header("Authorization", s"Bearer $accessToken")
      .header("Content-Type", "application/json")
      .postData(s"""
           |{
           |  "Amount": $amount,
           |  "Comment": "$comment",
           |  "PaymentId": "$paymentId",
           |  "Type": "Electronic"
           |}
           |""".stripMargin)
      .method("POST")
      .asString
      .body
      .pipe{body =>
        System.out.println(s"RefundResult Body was $body")
        read[RefundResult](body)
      }
      .Id
  }

  def getRefundStatus(refundId: String): String = {
    Http(s"$zuoraApiHost/v1/object/refund/$refundId")
      .header("Authorization", s"Bearer $accessToken")
      .asString
      .body
      .pipe(read[Refund](_))
      .Status
  }

  def getInvoices(accountId: String): List[Invoice] =
    Http(s"$zuoraApiHost/v1/action/query")
      .header("Authorization", s"Bearer $accessToken")
      .header("Content-Type", "application/json")
      .postData(
        s"""{"queryString": "select Id, Amount, Balance, InvoiceDate, InvoiceNumber, PaymentAmount, TargetDate, Status from Invoice where AccountId = '$accountId' and Status = 'Posted'"}"""
      )
      .method("POST")
      .asString
      .body
      .pipe(read[InvoiceQueryResult](_))
      .records

  def getPayments(accountId: String, paymentDate: LocalDate): List[Payment] =
    Http(s"$zuoraApiHost/v1/transactions/payments/accounts/$accountId")
      .header("Authorization", s"Bearer $accessToken")
      .asString
      .body
      .pipe(read[Payments](_))
      .payments
      .filter(payment => payment.status == "Processed" && payment.effectiveDate == paymentDate)

  def transferToCreditBalance(
      invoiceNumber: String,
      amount: BigDecimal,
      comment: String
  ): Unit =
    Http(s"$zuoraApiHost/v1/object/credit-balance-adjustment")
      .header("Authorization", s"Bearer $accessToken")
      .header("Content-Type", "application/json")
      .postData(s"""
           |{
           |  "Amount": $amount,
           |  "Comment": "$comment",
           |  "SourceTransactionNumber": "$invoiceNumber",
           |  "Type": "Increase"
           |}
           |""".stripMargin)
      .method("POST")
      .asString
      .body
      .pipe(body => read[RefundResult](body))

  def applyCreditBalance(invoiceNumber: String, amount: BigDecimal, comment: String): Unit =
    Http(s"$zuoraApiHost/v1/object/credit-balance-adjustment")
      .header("Authorization", s"Bearer $accessToken")
      .header("Content-Type", "application/json")
      .postData(s"""
           |{
           |  "Amount": $amount,
           |  "Comment": "$comment",
           |  "SourceTransactionNumber": "$invoiceNumber",
           |  "Type": "Decrease"
           |}
           |""".stripMargin)
      .method("POST")
      .asString
      .body
      .pipe(body => read[RefundResult](body))
}
