package com.gu.invoicing.pdf

import com.gu.invoicing.common.ZuoraAuth.{accessToken, zuoraApiHost}
import com.gu.invoicing.pdf.Model._
import com.gu.invoicing.common.Http
import scala.util.chaining._

object Impl {
  def getInvoice(invoiceId: String): Invoice =
    Http(s"$zuoraApiHost/v1/object/invoice/$invoiceId")
      .header("Authorization", s"Bearer ${accessToken}")
      .asString
      .body
      .pipe(read[Invoice](_))

  def getIdentityId(accountId: String): String =
    Http(s"$zuoraApiHost/v1/accounts/$accountId")
      .header("Authorization", s"Bearer ${accessToken}")
      .asString
      .body
      .pipe(read[Account](_))
      .basicInfo
      .IdentityId__c

  val noCache = Map( // https://stackoverflow.com/a/2068407/5205022
    "Cache-Control" -> "no-cache, no-store, must-revalidate",
    "Pragma" -> "no-cache",
    "Expires" -> "0"
  )
}
