package com.gu.invoicing.refundErroneousPayment

import com.gu.invoicing.common.Http
import com.gu.invoicing.common.ZuoraAuth.{accessToken, zuoraApiHost}
import com.gu.invoicing.refundErroneousPayment.Model._

import scala.util.chaining._

/**
  * Zuora API client and implementation details
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

  def createRefundObject(amount: BigDecimal,
                         paymentId: String,
                         comment: String): String = {
    Http(s"$zuoraApiHost/v1/object/refund")
      .header("Authorization", s"Bearer $accessToken")
      .header("Content-Type", "application/json")
      .postData(
        s"""
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
      .pipe(body => read[RefundResult](body))
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

  def getBalancingInvoice(accountId: String, invoiceAmount:BigDecimal):String =
    Http(s"$zuoraApiHost/v1/action/query")
      .header("Authorization", s"Bearer $accessToken")
      .header("Content-Type", "application/json")
      .postData(s"""{"queryString": "SELECT Id, Amount, Balance, InvoiceDate, InvoiceNumber, PaymentAmount, TargetDate, Status FROM Invoice WHERE AccountId = '$accountId' AND status = 'Posted'"}""")
      .method("POST")
      .asString
      .body
      .pipe(read[InvoiceQueryResult](_))
      .records
      .sortBy(_.InvoiceDate)
      .reverse
      .take(2) match {
      case neg :: pos :: Nil if neg.Amount == -invoiceAmount && pos.Amount == invoiceAmount => neg.InvoiceNumber
    }

  def transferToCreditBalance(invoiceNumber:String, invoiceAmount:BigDecimal, comment:String):Unit =
    Http(s"$zuoraApiHost/v1/object/credit-balance-adjustment")
      .header("Authorization", s"Bearer $accessToken")
      .header("Content-Type", "application/json")
      .postData(
        s"""
           |{
           |  "Amount": $invoiceAmount,
           |  "Comment": "$comment",
           |  "SourceTransactionNumber": "$invoiceNumber",
           |  "Type": "Increase"
           |}
           |""".stripMargin)
      .method("POST")
      .asString
      .body
      .pipe(body => read[RefundResult](body))

  def applyCreditBalance(balancingInvoiceNumber:String, invoiceAmount:BigDecimal, comment:String) :Unit =
    Http(s"$zuoraApiHost/v1/object/credit-balance-adjustment")
      .header("Authorization", s"Bearer $accessToken")
      .header("Content-Type", "application/json")
      .postData(
        s"""
           |{
           |  "Amount": $invoiceAmount,
           |  "Comment": "$comment",
           |  "SourceTransactionNumber": "$balancingInvoiceNumber",
           |  "Type": "Decrease"
           |}
           |""".stripMargin)
      .method("POST")
      .asString
      .body
      .pipe(body => read[RefundResult](body))
}
