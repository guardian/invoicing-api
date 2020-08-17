package com.gu.invoicing.refund

import Model._
import Impl._
import Program._
import scala.util.chaining._
import java.io.{InputStream, OutputStream}
import java.nio.charset.Charset
import java.util.logging.Logger

/**
 * Run refund process as AWS lambda.
 * https://aws.amazon.com/blogs/compute/writing-aws-lambda-functions-in-scala/
 *
 * This lambda is used in API Gateway as Lambda Proxy Integration. The input format of the request is
 * https://docs.aws.amazon.com/apigateway/latest/developerguide/set-up-lambda-proxy-integrations.html#api-gateway-simple-proxy-for-lambda-input-format
 *
 * Input schema https://docs.aws.amazon.com/apigateway/latest/developerguide/set-up-lambda-proxy-integrations.html#api-gateway-simple-proxy-for-lambda-input-format
 *
 * {
 *   "resource": "Resource path",
 *   "path": "Path parameter",
 *   "httpMethod": "Incoming request's method name"
 *   "headers": {String containing incoming request headers}
 *   "multiValueHeaders": {List of strings containing incoming request headers}
 *   "queryStringParameters": {query string parameters }
 *   "multiValueQueryStringParameters": {List of query string parameters}
 *   "pathParameters":  {path parameters}
 *   "stageVariables": {Applicable stage variables}
 *   "requestContext": {Request context, including authorizer-returned key-value pairs}
 *   "body": "A JSON string of the request payload."
 *   "isBase64Encoded": "A boolean flag to indicate if the applicable request payload is Base64-encode"
 * }
 *
 * Output schema https://docs.aws.amazon.com/apigateway/latest/developerguide/set-up-lambda-proxy-integrations.html#api-gateway-simple-proxy-for-lambda-output-format
 *
 * {
 *   "isBase64Encoded": true|false,
 *   "statusCode": httpStatusCode,
 *   "headers": { "headerName": "headerValue", ... },
 *   "multiValueHeaders": { "headerName": ["headerValue", "headerValue2", ...], ... },
 *   "body": "..."
 * }
 *
 * Note the key 'body'
 *
 *   "body": "A JSON string of the request payload."
 *
 * This is where the JSON body of the HTTP request is mapped to, however its value has to be a String of JSON,
 * not actual JSON. This means to put JSON inside String we have to escape it
 * https://stackoverflow.com/a/52240132/5205022
 *
 * Example test event for running the lambda from AWS Console:
 * {
 *   "body": "{\"subscriptionName\": \"A-S00045160\",\"refund\": 0.1}"
 * }
 */
object Lambda {
  def handleRequest(input: InputStream, output: OutputStream): Unit = {
    input
      .pipe { deserialiseStream }
      .tap  { info[RefundInput] }
      .pipe { program }
      .tap  { info[RefundOutput] }
      .pipe { serialiseToStream }

    def deserialiseStream(inputStream: InputStream): RefundInput = {
      inputStream
        .pipe { read[ApiGatewayInput](_) }
        .body
        .pipe { read[RefundInput](_) }
    }
    def serialiseToStream(refundOutput: RefundOutput): Unit  = {
      refundOutput
        .pipe { refundOut => ApiGatewayOutput(200, write(refundOut)) }
        .pipe { apiGatewayOut => write(apiGatewayOut) }
        .pipe { _.getBytes }
        .pipe { output.write }
    }
  }
}
