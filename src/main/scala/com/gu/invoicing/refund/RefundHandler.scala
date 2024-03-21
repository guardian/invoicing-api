package com.gu.invoicing.refund

import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse.BatchItemFailure
import com.amazonaws.services.lambda.runtime.events.{APIGatewayProxyRequestEvent, SQSBatchResponse, SQSEvent}
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import cats.data.EitherT
import io.circe.generic.codec.DerivedAsObjectCodec.deriveCodec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._
import io.circe.parser.{decode => circeDecode}
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

class RefundHandler  extends RequestHandler[SQSEvent, Unit] {

  override def handleRequest(input: SQSEvent, context: Context): Unit = {
    System.out.println("Handling request")

    val messages = input.getRecords.asScala.toList
    System.out.println(s"Received ${messages.length} messages")
    System.out.println(s"Received the message - ${messages }")

    val failedMessageIds = messages.map(message => processEvent(message , context))
      .collect { case Left(messageId) => messageId }

    new SQSBatchResponse(
      failedMessageIds.map(messageId => new BatchItemFailure(messageId)).asJava,
    )
  }

  sealed trait Error

  case class ParseError(error: String) extends Error

  case class InvoicingRefundError(error: String) extends Error

  type SQSMessageId = String

  private def processEvent(message: SQSMessage, context: Context): Either[SQSMessageId, Unit] = {
    val rawBody = message.getBody
    System.out.println(s"Processing event: $rawBody")

    val result: EitherT[Future, Error, Unit] = EitherT
      .fromEither[Future](circeDecode[APIGatewayProxyRequestEvent](rawBody))
      .leftMap[Error](error => ParseError(error.getMessage))
      .flatMap(event => {
        System.out.println(s"Event: $event")
        event match {
          case _ => EitherT.rightT[Future, Error](())
        }
      } )


    val resultEither: Either[SQSMessageId, Unit] = Try(Await.result(result.value, 20.seconds)) match {
      case Success(Right(_)) =>
        System.out.println(s"Successfully processed the event: $rawBody")
        Right(())
      case Success(Left(ParseError(error))) =>
        System.out.println(s"Failed to process an event due to ParseError($error)")
        System.out.println(s"Failed event was: $rawBody")
        Left(message.getMessageId)
      case Failure(error) =>
        System.out.println(s"Failed to process an event due to Failure($error)")
        System.out.println(s"Failed event was: $rawBody")
        Left(message.getMessageId)
    }
    resultEither
  }
}

