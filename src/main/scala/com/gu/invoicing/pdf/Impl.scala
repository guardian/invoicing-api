package com.gu.invoicing.pdf

import com.gu.invoicing.common.ZuoraAuth.{accessToken, zuoraApiHost, GNMAustralia_InvoiceTemplateID}
import com.gu.invoicing.pdf.Model._
import com.gu.invoicing.common.Http
import scala.util.chaining._

object Impl {
  def getInvoice(invoiceId: String): Invoice =
    Http(s"$zuoraApiHost/v1/object/invoice/$invoiceId")
      .header("Authorization", s"Bearer $accessToken")
      .asString
      .body
      .pipe(read[Invoice](_))

  def regenerateInvoice(invoiceId: String): PutResponse =
    Http(s"$zuoraApiHost/v1/object/invoice/$invoiceId")
      .header("Authorization", s"Bearer $accessToken")
      .put(
        """{"RegenerateInvoicePDF":true}"""
      )
      .asString
      .body
      .pipe(read[PutResponse](_))

  def setGNMAustraliaInvoiceTemplateId(accountId: String): PutResponse = {
    Http(s"$zuoraApiHost/v1/object/account/$accountId")
      .header("Authorization", s"Bearer $accessToken")
      .put(
        s"""{"InvoiceTemplateId":"$GNMAustralia_InvoiceTemplateID"}"""
      )
      .asString
      .body
      .pipe(read[PutResponse](_))
  }

  def getAccount(accountId: String): Account =
    Http(s"$zuoraApiHost/v1/accounts/$accountId")
      .header("Authorization", s"Bearer $accessToken")
      .asString
      .body
      .pipe(read[Account](_))

  val noCache: Map[String, String] = Map( // https://stackoverflow.com/a/2068407/5205022
    "Cache-Control" -> "no-cache, no-store, must-revalidate",
    "Pragma" -> "no-cache",
    "Expires" -> "0"
  )
}
