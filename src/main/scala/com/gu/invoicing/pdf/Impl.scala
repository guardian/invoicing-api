package com.gu.invoicing.pdf

import java.lang.System.getenv
import com.gu.invoicing.pdf.Model._
import scalaj.http.Http
import scala.util.chaining._

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
}
