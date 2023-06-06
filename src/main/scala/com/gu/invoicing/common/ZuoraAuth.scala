package com.gu.invoicing.common

import java.lang.System.getenv
import java.util.{Timer, TimerTask}
import com.gu.invoicing.common.Retry._

import scala.util.chaining._
import scalaj.http.Http

import scala.util.{Failure, Success}

object ZuoraAuth extends JsonSupport {
  case class Config(clientId: String, clientSecret: String)
  case class AccessToken(access_token: String)
  implicit val configRW: ReadWriter[Config] = macroRW
  implicit val accessTokenRW: ReadWriter[AccessToken] = macroRW

  private lazy val stage = getenv("Stage")
  private lazy val config = read[Config](getenv("Config"))

  lazy val zuoraApiHost: String =
    stage match {
      case "CODE" => "https://rest.apisandbox.zuora.com";
      case "PROD" => "https://rest.zuora.com"
    }

  lazy val GNMAustralia_InvoiceTemplateID: String =
    stage match {
      case "CODE" => "2c92c0f85ecc47e5015ee7360d602757"
      case "PROD" => "2c92a0fd5ecce80c015ee71028643020"
    } // GNM Australia Pty Ltd

  /** Because list invoices is hit frequently JVM is kept warm and val access token would seem to persist across lambda
    * executions which meant the token would expire after one hour and because it was val it would not be requested
    * again. Hence now we periodically refreshes the token (making it def would have performance penalty)
    */
  var accessToken: String = _
  private def getAccessToken(): String = {
    Http(s"$zuoraApiHost/oauth/token")
      .postForm(
        Seq(
          "client_id" -> config.clientId,
          "client_secret" -> config.clientSecret,
          "grant_type" -> "client_credentials",
        ),
      )
      .asString
      .body
      .pipe(read[AccessToken](_))
      .access_token
  }
  private val timer = new Timer()
  private def ZuoraOutageWarning(cause: Throwable) = new RuntimeException(
    """
      |Failed to authenticate with Zuora after multiple retries.
      |Possible zuora outage. Investigate ASAP! https://trust.zuora.com
      |""".stripMargin,
    cause,
  )
  timer.schedule(
    new TimerTask {
      def run(): Unit = {
        retry(getAccessToken()) // do not update cache on failure
          .foreach(token => accessToken = token)
      }
    },
    0,
    5 * 60 * 1000, // refresh token every 5 min
  )
  retry(getAccessToken()) match { // set token on initialization
    case Success(token) => accessToken = token
    case Failure(e) => throw ZuoraOutageWarning(e)
  }
}
