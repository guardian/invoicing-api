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

  /** Because list invoices is hit frequently JVM is kept warm and val access token would seem to persist across lambda
    * executions which meant the token would expire after one hour and because it was val it would not be requested
    * again. Hence now we periodically refreshes the token (making it def would have performance penalty)
    */
  var accessToken: String = _
  private def getAccessToken: String = {
    val logger = java.util.logging.Logger.getGlobal
    val authUrl = s"$zuoraApiHost/oauth/token"
    logger.info(s"Authenticating with Zuora on $authUrl with client ID ending in: ...${config.clientId.takeRight(4)}")
    Http(authUrl)
      .postForm(
        Seq(
          "client_id" -> config.clientId,
          "client_secret" -> config.clientSecret,
          "grant_type" -> "client_credentials",
        ),
      )
      .asString
      .body
      .tap { body =>
        // although cloudwatch is pretty secure, it's a nice thing to redact if possible
        val redactedBody = body.replaceAll(""""access_token":"[^"]*"""", """"access_token":"***REDACTED***"""")
        logger.info(redactedBody)
      }
      .pipe(read[AccessToken](_))
      .access_token
  }
  private val timer = new Timer(true)
  private def ZuoraOutageWarning(cause: Throwable) = new RuntimeException(
    """
      |Failed to authenticate with Zuora after multiple retries.
      |Possible zuora outage. Investigate ASAP! https://trust.zuora.com
      |""".stripMargin,
    cause,
  )

  private val five_minutes: Int = 5 * 60 * 1000

  timer.schedule(
    new TimerTask {
      def run(): Unit = {
        retry(getAccessToken) // do not update cache on failure
          .foreach(token => accessToken = token)
      }
    },
    five_minutes, // wait 5 mins for first refresh
    five_minutes, // refresh token every 5 min
  )
  retry(getAccessToken) match { // set token on initialization
    case Success(token) => accessToken = token
    case Failure(e) => throw ZuoraOutageWarning(e)
  }
}
