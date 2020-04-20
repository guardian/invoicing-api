package com.gu.invoicing.refund

import java.lang.System.getenv
import java.time.LocalDate
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import scalaj.http.{BaseHttp, HttpOptions}
import upickle.default.{read, write}
import scala.annotation.tailrec
import scala.io.Source
import Model._
import scala.util.chaining._

/**
 * Zuora API client and implementation details
 */
object Impl {
  val stage = getenv("Stage")

  def readConfig(): Config = {
    val s3Client = AmazonS3ClientBuilder.standard().withRegion("eu-west-1").build()
    val bucketName = "gu-reader-revenue-private"
    val key = s"membership/support-service-lambdas/$stage/zuoraRest-$stage.v1.json"
    val inputStream = s3Client.getObject(bucketName, key).getObjectContent
    val rawJson = Source.fromInputStream(inputStream).mkString
    read[Config](rawJson)
  }

  val zuoraApiHost: String =
    stage match { case "CODE" => "https://rest.apisandbox.zuora.com"; case "PROD" => "https://rest.zuora.com" }

  object HttpWithLongTimeout extends BaseHttp(
    options = Seq(
      HttpOptions.connTimeout(5000),
      HttpOptions.readTimeout(5 * 60 * 1000),
      HttpOptions.followRedirects(false)
    )
  )

  def accessToken(): String = {
    val oauthConfig = readConfig().zuoraDatalakeExport.oauth
    HttpWithLongTimeout(s"$zuoraApiHost/oauth/token")
      .postForm(Seq(
        "client_id" -> oauthConfig.clientId,
        "client_secret" -> oauthConfig.clientSecret,
        "grant_type" -> "client_credentials"
      ))
      .asString
      .body
      .pipe(read[AccessToken](_))
      .access_token
  }

  def getSubscription(name: String): Subscription = {
    HttpWithLongTimeout(s"$zuoraApiHost/v1/subscriptions/$name")
      .header("Authorization", s"Bearer ${accessToken()}")
      .asString
      .body
      .pipe(read[Subscription](_))
  }

  def getAccountBalance(accountId: String): Double = {
    HttpWithLongTimeout(s"$zuoraApiHost/v1/accounts/$accountId")
      .header("Authorization", s"Bearer ${accessToken()}")
      .asString
      .body
      .pipe(read[Account](_))
      .metrics
      .balance
  }

  def getInvoices(accountId: String): List[Invoice] = {
    HttpWithLongTimeout(s"$zuoraApiHost/v1/action/query")
      .header("Authorization", s"Bearer ${accessToken()}")
      .header("Content-Type", "application/json")
      .postData(s"""{"queryString": "select Id, Amount, Balance, InvoiceDate, InvoiceNumber, PaymentAmount, TargetDate, Status from Invoice where AccountId = '$accountId'"}""")
      .method("POST")
      .asString
      .body
      .pipe(read[InvoiceQueryResult](_))
      .records
  }

  def getInvoiceItems(invoiceId: String): List[InvoiceItem] = {
    HttpWithLongTimeout(s"$zuoraApiHost/v1/invoices/$invoiceId/items")
      .header("Authorization", s"Bearer ${accessToken()}")
      .asString
      .body
      .pipe(read[InvoiceItems](_))
      .invoiceItems
  }

  def getItemsByInvoice(subscriptionName: String): Map[String, List[InvoiceItem]] = {
    HttpWithLongTimeout(s"$zuoraApiHost/v1/action/query")
      .header("Authorization", s"Bearer ${accessToken()}")
      .header("Content-Type", "application/json")
      .postData(s"""{"queryString": "select Id, ChargeAmount, ChargeDate, ChargeName, ChargeNumber, InvoiceId, ProductName, ServiceEndDate, ServiceStartDate, SubscriptionNumber FROM InvoiceItem where SubscriptionNumber = '$subscriptionName'"}""".stripMargin)
      .method("POST")
      .asString
      .body
      .pipe(read[InvoiceItemQueryResult](_))
      .records
      .groupBy(_.InvoiceId)
  }

  def getInvoicePaymentId(invoiceId: String): Option[String] = {
    HttpWithLongTimeout(s"$zuoraApiHost/v1/action/query")
      .header("Authorization", s"Bearer ${accessToken()}")
      .header("Content-Type", "application/json")
      .postData(s"""{"queryString": "select Id, invoiceId, paymentId from InvoicePayment where invoiceId = '$invoiceId'"}""")
      .method("POST")
      .asString
      .body
      .pipe(read[InvoicePaymentQueryResult](_))
      .records
      .headOption
      .map(_.PaymentId)
    //      .getOrElse(throw new Exception(s"There should be one invoice payment against invoice $invoiceId"))
  }

  def createRefundObject(amount: Double, paymentId: String, comment: String): String = {
    HttpWithLongTimeout(s"$zuoraApiHost/v1/object/refund")
      .header("Authorization", s"Bearer ${accessToken()}")
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
    HttpWithLongTimeout(s"$zuoraApiHost/v1/object/refund/$refundId")
      .header("Authorization", s"Bearer ${accessToken()}")
      .asString
      .body
      .pipe(read[Refund](_))
      .Status
  }

  def netAdjustmentsByInvoiceItemId(adjustments: List[InvoiceItemAdjustment]): Map[String, Double] = {
    adjustments
      .groupBy(_.SourceId)
      .map { case (invoiceItemId, adjustments) =>
        val netCredits = adjustments.filter(_.Type == "Credit").map(_.Amount).sum
        val netCharges = adjustments.filter(_.Type == "Charge").map(_.Amount).sum
        (invoiceItemId, netCredits - netCharges)
      }
  }

  def spreadRefundAcrossItems(
    invoiceItems: List[InvoiceItem],
    adjustments: List[InvoiceItemAdjustment],
    totalRefundAmount: Double,
    refundGuid: String,
  ): List[InvoiceItemAdjustmentWrite] = {

    /* Collect all item adjustments of a particular invoice item and return remaining amount that can be adjusted/refunded */
    def availableAmount(invoiceItem: InvoiceItem): Option[Double] = {
      netAdjustmentsByInvoiceItemId(adjustments).get(invoiceItem.Id) match {
        case Some(netAdjustment) =>
          val availableRefundableAmount = invoiceItem.ChargeAmount - netAdjustment
          if (availableRefundableAmount <= 0.0) None else Some(availableRefundableAmount)

        case None => // this items has not been adjusted therefore the original full item amount is available
          Some(invoiceItem.ChargeAmount)
      }
    }

    @tailrec def loop(remainingAmounToRefund: Double, remainingItems: List[InvoiceItem], accumulatedAdjustments: List[InvoiceItemAdjustmentWrite]): List[InvoiceItemAdjustmentWrite] = {
      remainingItems match {
        case Nil =>
          accumulatedAdjustments

        case nextItem :: tail =>
          val adjustItemBy: Double => InvoiceItemAdjustmentWrite =
            InvoiceItemAdjustmentWrite(LocalDate.parse("2020-04-14" /* FIXME */), _, refundGuid, nextItem.InvoiceId, "Credit", "InvoiceDetail", nextItem.Id)

          availableAmount(nextItem) match {
            case Some(availableRefundableAmount) =>
              if ((remainingAmounToRefund - availableRefundableAmount) <= 0.0)
                adjustItemBy(remainingAmounToRefund) :: accumulatedAdjustments
              else
                loop(remainingAmounToRefund - availableRefundableAmount, tail, adjustItemBy(availableRefundableAmount) :: accumulatedAdjustments)

            case None =>
              loop(remainingAmounToRefund, tail, accumulatedAdjustments)
          }

      }
    }

    loop(totalRefundAmount, invoiceItems, List.empty[InvoiceItemAdjustmentWrite])
  }

  def applyRefundOverItemAdjustments(invoiceItems: List[InvoiceItemAdjustmentWrite]): List[AdjustmentResult] = {
    HttpWithLongTimeout(s"$zuoraApiHost/v1/action/create")
      .header("Authorization", s"Bearer ${accessToken()}")
      .header("Content-Type", "application/json")
      .postData(write(InvoiceItemAdjustmentsWriteRequest(objects = invoiceItems, `type` = "InvoiceItemAdjustment")))
      .method("POST")
      .asString
      .body
      .pipe(read[List[AdjustmentResult]](_))
  }

  def joinInvoiceWithInvoiceItemsOnInvoiceIdKey(invoices: List[Invoice], invoiceItemsBySubscription: Map[String, List[InvoiceItem]]): List[(String, Invoice, List[InvoiceItem])] = {
    for {
      (invoiceId, invoice) <- invoices.map(invoice => invoice.Id -> invoice)
      (`invoiceId`, invoiceItems) <- invoiceItemsBySubscription
    } yield {
      (invoiceId, invoice, invoiceItems)
    }
  }

  /** Select correct invoice to apply refund to */
  def decideRelevantInvoice(invoices: List[Invoice], invoiceItemsBySubscription: Map[String, List[InvoiceItem]]): (String, Invoice, List[InvoiceItem]) = {
    joinInvoiceWithInvoiceItemsOnInvoiceIdKey(invoices, invoiceItemsBySubscription)
      .iterator
      .filter({ case (invoiceId, invoice, invoiceItems) => invoice.Status == "Posted"})
      .filter({ case (invoiceId, invoice, invoiceItems) => invoice.Amount > 0.0})
      .maxBy({ case (invoiceId, invoice, invoiceItems) => invoice.TargetDate })
  }

  def getInvoiceItemAdjustments(invoiceId: String): List[InvoiceItemAdjustment] = {
    HttpWithLongTimeout(s"$zuoraApiHost/v1/action/query")
      .header("Authorization", s"Bearer ${accessToken()}")
      .header("Content-Type", "application/json")
      .postData(s"""{"queryString": "select Id, InvoiceId, InvoiceItemName, SourceId, SourceType, Status, Type, Amount FROM InvoiceItemAdjustment where InvoiceId = '$invoiceId'"}""")
      .method("POST")
      .asString
      .body
      .pipe(read[InvoiceItemAdjustmentsQueryResult](_))
      .records
  }

  def roundHalfUp(x: Double) = BigDecimal(x).setScale(5, BigDecimal.RoundingMode.HALF_UP).toDouble

  // https://knowledgecenter.zuora.com/Billing/Billing_and_Payments/TB_Rounding_and_Precision
  def roundAdjustments(adjustments: List[InvoiceItemAdjustmentWrite]): List[InvoiceItemAdjustmentWrite] = {
    adjustments
      .filter(a => roundHalfUp(a.Amount) != 0)
      .map(a => a.copy(Amount = roundHalfUp(a.Amount)))
  }

}
