import scalaj.http.{BaseHttp, Http, HttpOptions, HttpResponse}
import scala.util.chaining._

/**
 * https://docs.aws.amazon.com/lambda/latest/dg/runtimes-walkthrough.html
 */
object bootstrap extends upickle.AttributeTagged {
  /* Option - http://www.lihaoyi.com/upickle/#CustomConfiguration */
  implicit def optionWriter[T: Writer]: Writer[Option[T]] =
    implicitly[Writer[T]].comap[Option[T]] {
      case None => null.asInstanceOf[T]
      case Some(x) => x
    }

  implicit def optionReader[T: Reader]: Reader[Option[T]] = {
    new Reader.Delegate[Any, Option[T]](implicitly[Reader[T]].map(Some(_))) {
      override def visitNull(index: Int) = None
    }
  }

  case class RequestIdentity(
    apiKey: Option[String],
    userArn: Option[String],
    cognitoAuthenticationType: Option[String],
    caller: Option[String],
    userAgent: Option[String],
    user: Option[String],
    cognitoIdentityPoolId: Option[String],
    cognitoAuthenticationProvider: Option[String],
    sourceIp: Option[String],
    accountId: Option[String],
  )

  case class RequestContext(
    resourceId: String,
    apiId: String,
    resourcePath: String,
    httpMethod: String,
    accountId: String,
    stage: String,
    identity: RequestIdentity,
    extendedRequestId: Option[String],
    path: String
  )

  // The request returned from the next-event url
  case class RequestEvent(
    httpMethod: String,
    body: Option[String],
    resource: String,
    requestContext: RequestContext,
    queryStringParameters: Option[Map[String, String]],
    headers: Option[Map[String, String]],
    pathParameters: Option[Map[String, String]],
    stageVariables: Option[Map[String, String]],
    path: String,
    isBase64Encoded: Boolean
  )
  case class LambdaResponse(
    statusCode: String,
    headers: Map[String, String],
    body: String,
    isBase64Encoded: Boolean = false
  )

  implicit val RequestIdentityRW: ReadWriter[RequestIdentity]  = macroRW
  implicit val RequestContextRW: ReadWriter[RequestContext]  = macroRW
  implicit val RequestEventRW: ReadWriter[RequestEvent]  = macroRW
  implicit val LambdaResponseRW: ReadWriter[LambdaResponse]  = macroRW

  def main(args: Array[String]): Unit = {
    val runtimeApiHost = sys.env("AWS_LAMBDA_RUNTIME_API")
    println(runtimeApiHost)
    println(sys.env("_HANDLER"))
    handleEvents(runtimeApiHost)
  }

  // Receive and handle events infinitely
  def handleEvents(runtimeApiHost: String): Unit = {
    val nextEventUrl = getNextEventUrl(runtimeApiHost)
    while (true) {
      val r = Http(nextEventUrl).asString
      println("woohoo:")
      println(sys.env("AWS_LAMBDA_RUNTIME_API"))
      println(sys.env("_HANDLER"))
      println(r.body)
      val requestId = r.header("lambda-runtime-aws-request-id").getOrElse(throw new RuntimeException("Missing lambda-runtime-aws-request-id. Fix ASAP!"))
      println(s"requestId = $requestId")
      val result = sys.env("_HANDLER") match {
        case "com.gu.invoicing.nextinvoicedate.Lambda::handleRequest" => com.gu.invoicing.nextinvoicedate.Lambda.handleRequest(r.body)
        case handler => throw new RuntimeException(s"Unknown function handler in custom runtime: $handler")
      }
      println(s"ratatata = $result")
      val awsRuntimeResult = Http(getResponseUrl(runtimeApiHost, requestId)).postData(result).asString
      println(s"awsRuntimeResult = $awsRuntimeResult")
    }
  }

  // Retrieve the specified header from the response. If not available, report the header as missing
  def getRequiredHeader(nextEventResponse: HttpResponse[String], header: String): Option[String] = {
    nextEventResponse.headers.get(header).map(_.head).orElse {
      Console.err.println(s"Next event request did not include this required header: $header")
      Console.err.println(s"headers: ${nextEventResponse.headers}")
      None
    }
  }

  // Handle a valid request to this function
  def handleRequest(host: String, requestEvent: RequestEvent, requestId: String, deadlineMs: Long): Int = {
    requestEvent.pathParameters.flatMap(_.get("name")) match {
      case Some(name) =>
        val response = LambdaResponse("200", Map("Content-Type" -> "text/plain"), s"Hello, $name!\n")
        Http(getResponseUrl(host, requestId))
          .postData(write[LambdaResponse](response))
          .asString
          .code
      case None =>
        val response = LambdaResponse("400", Map("Content-Type" -> "text/plain"), "'name' param not found\n")
        Http(getResponseUrl(host, requestId))
          .postData(write[LambdaResponse](response))
          .asString
          .code
    }
  }

  // The url used to retrieve the next function request
  def getNextEventUrl(host: String) =
    s"http://$host/2018-06-01/runtime/invocation/next"

  // The url used to write a response back to the caller
  def getResponseUrl(host: String, requestId: String) =
    s"http://$host/2018-06-01/runtime/invocation/$requestId/response"
}
