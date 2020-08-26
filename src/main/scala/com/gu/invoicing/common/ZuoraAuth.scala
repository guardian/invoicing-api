package com.gu.invoicing.common

import java.lang.System.getenv
import java.util.{Timer, TimerTask}

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

  /**
   * Because list invoices is hit frequently JVM is kept warm and val access token
   * would seem to persist across lambda executions which meant the token would expire
   * after one hour and because it was val it would not be requested again. Hence now
   * we periodically refreshes the token (making it def would have performance penalty)
   */
  var accessToken: String = _
  private def getAccessToken(): String = {
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
  private val timer = new Timer()
  timer.schedule(
    new TimerTask { def run(): Unit = accessToken = getAccessToken() },
    0, 5 * 60 * 1000 // refresh token every 5 min
  )
  accessToken = getAccessToken() // set token on initialization
}
