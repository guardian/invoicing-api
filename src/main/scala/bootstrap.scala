import scalaj.http.Http
import scala.util.chaining._

/**
 * Custom AWS Lambda runtime for faster Scala lambda cold start and smaller memory footprint
 * https://docs.aws.amazon.com/lambda/latest/dg/runtimes-custom.html
 */
object bootstrap {
  def main(args: Array[String]): Unit = {
    val runtimeHostAndPort = sys.env("AWS_LAMBDA_RUNTIME_API")
    val handlerMethod      = sys.env("_HANDLER") // corresponds to Handler property of AWS::Lambda::Function
    val customRuntimeUrl   = s"http://$runtimeHostAndPort/2018-06-01/runtime/invocation"
    while (true) {
      val input     = Http(s"$customRuntimeUrl/next").asString
      val requestId = input.header("lambda-runtime-aws-request-id").getOrElse(throw new RuntimeException("Missing lambda-runtime-aws-request-id. Fix ASAP!"))
      val output    = executeLambda(handlerMethod, input.body)
      val _         = Http(s"$customRuntimeUrl/$requestId/response").postData(output).asString
    }
  }
  private def executeLambda(handler: String, input: String): String = {
    handler match {
      case "com.gu.invoicing.nextinvoicedate.Lambda::handleRequest" =>
        com.gu.invoicing.nextinvoicedate.Lambda.handleRequest(input)

      case "com.gu.invoicing.preview.Lambda::handleRequest" =>
        com.gu.invoicing.preview.Lambda.handleRequest(input)

      case "com.gu.invoicing.invoice.Lambda::handleRequest" =>
        com.gu.invoicing.invoice.Lambda.handleRequest(input)

      case "com.gu.invoicing.refund.Lambda::handleRequest" =>
        com.gu.invoicing.refund.Lambda.handleRequest(input)

      case unknownHandler =>
        throw new RuntimeException(s"Unknown function handler in custom runtime: $unknownHandler")
    }
  }
}
