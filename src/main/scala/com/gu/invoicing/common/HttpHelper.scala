package com.gu.invoicing.common

import com.amazonaws.services.lambda.runtime.events.{APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent}

import scala.jdk.CollectionConverters.MapHasAsScala

object HttpHelper {
  def pathParameterOrThrow(request: APIGatewayProxyRequestEvent, parameterName: String): String = {
    request.getPathParameters.asScala.getOrElse(
      parameterName,
      throw new RuntimeException(s"Parameter $parameterName was missing from the path ${request.getPath}"),
    )
  }

  def queryStringParameterOrThrow(request: APIGatewayProxyRequestEvent, parameterName: String): String = {
    request.getQueryStringParameters.asScala.getOrElse(
      parameterName,
      throw new RuntimeException(s"Query string Parameter $parameterName was missing from the url"),
    )
  }

  def headerOrThrow(request: APIGatewayProxyRequestEvent, headerName: String): String = {
    request.getHeaders.asScala.getOrElse(
      headerName,
      throw new RuntimeException(s"Header $headerName was missing from the request"),
    )
  }
  def okResponse(resultBody: String): APIGatewayProxyResponseEvent = {
    val response = new APIGatewayProxyResponseEvent()
    response.setStatusCode(200)
    response.setBody(resultBody)
    response
  }
}
