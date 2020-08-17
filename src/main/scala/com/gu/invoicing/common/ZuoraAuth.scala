package com.gu.invoicing.common

import java.lang.System.getenv
import scala.util.chaining._
import scalaj.http.Http

object ZuoraAuth extends JsonSupport {
  case class Config(clientId: String, clientSecret: String)
  case class AccessToken(access_token: String)
  implicit val configRW: ReadWriter[Config] = macroRW
  implicit val accessTokenRW: ReadWriter[AccessToken] = macroRW

  private lazy val stage = getenv("Stage")
  private lazy val config = read[Config](getenv("Config"))

  lazy val zuoraApiHost: String =
    stage match {
      case "DEV" | "CODE" => "https://rest.apisandbox.zuora.com";
      case "PROD" => "https://rest.zuora.com"
    }

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
}
