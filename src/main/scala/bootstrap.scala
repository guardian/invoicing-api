import com.gu.invoicing.common.Retry.retryUnsafe
import scalaj.http.{BaseHttp, HttpOptions, HttpResponse}
import scala.util.chaining._

/**
 * Custom AWS Lambda runtime for faster Scala lambda cold start and smaller memory footprint
 * https://docs.aws.amazon.com/lambda/latest/dg/runtimes-custom.html
 */
object bootstrap {
  private val runtimeHostAndPort = sys.env("AWS_LAMBDA_RUNTIME_API")
  private val lambda             = sys.env("_HANDLER") // corresponds to Handler property of AWS::Lambda::Function
  private val customRuntimeUrl   = s"http://$runtimeHostAndPort/2018-06-01/runtime/invocation"

  /**
   * HTTP client with infinite timeout is required as per
   * https://docs.aws.amazon.com/lambda/latest/dg/runtimes-api.html#runtimes-api-next:
   *
   *   "Do not set a timeout on the GET call. Between when Lambda bootstraps the runtime and when the runtime
   *    has an event to return, the runtime process may be frozen for several seconds."
   */
  private object Http extends BaseHttp(
    options = Seq(
      HttpOptions.connTimeout(0),
      HttpOptions.readTimeout(0),
    )
  )

  private def executeLambda(handler: String, input: String): String = {
    handler match {
      case "com.gu.invoicing.nextinvoicedate.Lambda::handleRequest" =>
        com.gu.invoicing.nextinvoicedate.Lambda.handleRequest(input)

      case "com.gu.invoicing.preview.Lambda::handleRequest" =>
        com.gu.invoicing.preview.Lambda.handleRequest(input)

      case "com.gu.invoicing.invoice.Lambda::handleRequest" =>
        com.gu.invoicing.invoice.Lambda.handleRequest(input)

      case "com.gu.invoicing.pdf.Lambda::handleRequest" =>
        com.gu.invoicing.pdf.Lambda.handleRequest(input)

      case "com.gu.invoicing.refund.Lambda::handleRequest" =>
        com.gu.invoicing.refund.Lambda.handleRequest(input)

      case unknownHandler =>
        throw new RuntimeException(s"Unknown function handler in custom runtime: $unknownHandler")
    }
  }

  // https://docs.aws.amazon.com/lambda/latest/dg/runtimes-api.html#runtimes-api-next
  private def getNextInvoicationEvent(): HttpResponse[String] = retryUnsafe {
    Http(s"$customRuntimeUrl/next")
      .asString
      .tap { response => assert(response.header("lambda-runtime-aws-request-id").isDefined) }
  }

  private def getRequestId(invocationEvent: HttpResponse[String]): String = {
    invocationEvent
      .header("lambda-runtime-aws-request-id")
      .getOrElse(throw new RuntimeException("Missing lambda-runtime-aws-request-id. Fix ASAP!"))
  }

  // https://docs.aws.amazon.com/lambda/latest/dg/runtimes-api.html#runtimes-api-response
  private def postInvocationResult(requestId: String, output: String): Unit = {
    Http(s"$customRuntimeUrl/$requestId/response")
      .postData(output)
      .asString
  }

  def main(args: Array[String]): Unit = {
    while (true) {
      val input     = getNextInvoicationEvent()
      val requestId = getRequestId(input)
      val output    = executeLambda(lambda, input.body)
      val _         = postInvocationResult(requestId, output)
    }
  }
}
