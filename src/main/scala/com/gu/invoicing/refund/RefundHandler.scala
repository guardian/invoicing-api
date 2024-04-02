package com.gu.invoicing.refund

import com.amazonaws.services.lambda.runtime.events.{APIGatewayProxyRequestEvent, SQSEvent}
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import io.circe.generic.codec.DerivedAsObjectCodec.deriveCodec
import scala.jdk.CollectionConverters._
import io.circe.parser.{decode => circeDecode}

class RefundHandler  extends RequestHandler[SQSEvent, Unit] {

  override def handleRequest(input: SQSEvent, context: Context): Unit = {
    System.out.println("Handling request")

    val messages = input.getRecords.asScala.toList
    System.out.println(s"Received ${messages.length} messages")
    System.out.println(s"Received the message - ${messages}")
    messages.foreach { message =>
      val rawBody = message.getBody
      System.out.println(s"Processing event: $rawBody")
      val maybeRefundInput = circeDecode[APIGatewayProxyRequestEvent](rawBody)

      maybeRefundInput match {
        case Right(refundInput) => Lambda.handleRequest(refundInput, context)
        case Left(ex) =>
          context.getLogger.log(s"Error '$ex' when decoding JSON to RefundInput with body: ${rawBody}")
      }
    }
  }

}

