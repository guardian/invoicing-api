package com.gu.invoicing.refund

import com.amazonaws.services.lambda.runtime.events.{APIGatewayProxyRequestEvent}

object APIGatewayProxyRequestEvent{

  import io.circe.generic.semiauto._
  import io.circe.{Decoder, Encoder}

  implicit val apiGatewayEventDecoder: Decoder[APIGatewayProxyRequestEvent] = deriveDecoder[APIGatewayProxyRequestEvent]
  implicit val apiGatewayEventEncoder: Encoder[APIGatewayProxyRequestEvent] = deriveEncoder[APIGatewayProxyRequestEvent]
}
