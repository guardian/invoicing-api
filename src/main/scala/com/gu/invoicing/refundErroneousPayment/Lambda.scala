package com.gu.invoicing.refundErroneousPayment

import com.amazonaws.services.lambda.runtime.events.{APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent}
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.gu.invoicing.refundErroneousPayment.Model._
import com.gu.invoicing.refundErroneousPayment.Program._

import scala.util.chaining._

/** Run refund process as AWS lambda. https://aws.amazon.com/blogs/compute/writing-aws-lambda-functions-in-scala/
  *
  * This lambda is used in API Gateway as Lambda Proxy Integration. The input format of the request is
  * https://docs.aws.amazon.com/apigateway/latest/developerguide/set-up-lambda-proxy-integrations.html#api-gateway-simple-proxy-for-lambda-input-format
  *
  * Input schema
  * https://docs.aws.amazon.com/apigateway/latest/developerguide/set-up-lambda-proxy-integrations.html#api-gateway-simple-proxy-for-lambda-input-format
  *
  * { "resource": "Resource path", "path": "Path parameter", "httpMethod": "Incoming request's method name" "headers":
  * {String containing incoming request headers} "multiValueHeaders": {List of strings containing incoming request
  * headers} "queryStringParameters": {query string parameters } "multiValueQueryStringParameters": {List of query
  * string parameters} "pathParameters": {path parameters} "stageVariables": {Applicable stage variables}
  * "requestContext": {Request context, including authorizer-returned key-value pairs} "body": "A JSON string of the
  * request payload." "isBase64Encoded": "A boolean flag to indicate if the applicable request payload is Base64-encode"
  * }
  *
  * Output schema
  * https://docs.aws.amazon.com/apigateway/latest/developerguide/set-up-lambda-proxy-integrations.html#api-gateway-simple-proxy-for-lambda-output-format
  *
  * { "isBase64Encoded": true|false, "statusCode": httpStatusCode, "headers": { "headerName": "headerValue", ... },
  * "multiValueHeaders": { "headerName": ["headerValue", "headerValue2", ...], ... }, "body": "..." }
  *
  * Note the key 'body'
  *
  * "body": "A JSON string of the request payload."
  *
  * This is where the JSON body of the HTTP request is mapped to, however its value has to be a String of JSON, not
  * actual JSON. This means to put JSON inside String we have to escape it https://stackoverflow.com/a/52240132/5205022
  *
  * Example test event for running the lambda from AWS Console: { "body":
  * "{\"accountId\":\"A123\",\"invoiceNumber\":\"INV123\",\"paymentId\":\"P123\",\"invoiceAmount\":0.1,\"comment\":"this
  * is comment"}" }
  */
object Lambda extends RequestHandler[APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent] {
  def handleRequest(input: APIGatewayProxyRequestEvent, context: Context): APIGatewayProxyResponseEvent = {
    {
      input.getBody
        .pipe { read[RefundInput](_) }
        .tap { info[RefundInput] }
        .pipe { program }
        .tap { info[RefundOutput] }
        .pipe { refundOut =>
          val response = new APIGatewayProxyResponseEvent
          response.setStatusCode(200)
          response.setBody(write(refundOut))
          response
        }
    }
  }
}
