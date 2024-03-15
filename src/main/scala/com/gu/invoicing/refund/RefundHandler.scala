package com.gu.invoicing.refund

import com.amazonaws.services.lambda.runtime.events.{APIGatewayProxyResponseEvent, SQSEvent}
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.gu.invoicing.refund.Model.RefundInput

import scala.jdk.CollectionConverters._
import io.circe.ParsingFailure
import io.circe.{Decoder, DecodingFailure}
import io.circe.generic.auto._
import io.circe.parser.{decode => circeDecode}
import io.circe.syntax._



class RefundHandler  extends RequestHandler[SQSEvent, Unit] {

  case class MessageBody(
                          subscriptionId: String,
                          productName: String,
                        )

  override def handleRequest(input: SQSEvent, context: Context): Unit = {
    System.out.println("Handling request")


    val messages = input.getRecords.asScala.toList
    System.out.println(s"Received ${messages.length} messages")
//      .map(message =>
//      circeDecode[MessageBody](message.getBody) match {
//        case Left(pf: ParsingFailure) =>
//          val exception = Error(
//            s"Error '${pf.message}' when decoding JSON to MessageBody with cause :${pf.getCause} with body: ${message.getBody}",
//          )
//          handleError(exception)
//        case Right(result) =>
//          logger.info(s"Decoded message body: $result")
//          result
//      },
//    )
  }
}




    //  override def handleRequest(input: SQSEvent, context: Context): APIGatewayProxyResponseEvent = {
//
//    val apiGatewayEvent = SQSEventDecoder.decodeSQSEvent(input)
//
//    val apiGatewayEvent = SQSEventDecoder.decodeSQSEvent(input)
//    // Now you have the single APIGatewayProxyRequestEvent to work with
//    apiGatewayEvent match {
//      case Some(event) => Lambda.handleRequest(event, context)
//      case None => new APIGatewayProxyResponseEvent().withStatusCode(404).withBody("No message found in SQS event")
//    }
//
//  }

