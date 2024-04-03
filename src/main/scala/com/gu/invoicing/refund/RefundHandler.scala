package com.gu.invoicing.refund

import com.amazonaws.services.lambda.runtime.events.{APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent, SQSEvent}
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import io.circe.generic.codec.DerivedAsObjectCodec.deriveCodec
import scala.jdk.CollectionConverters._
import io.circe.parser.{decode => circeDecode}
import com.gu.invoicing.common.HttpHelper.okResponse
import com.gu.invoicing.refund.Model._
import com.gu.invoicing.refund.Program._

import scala.util.chaining._
case class APIGatewayEvent (body : String)

class RefundHandler  extends RequestHandler[SQSEvent, Unit] {

  override def handleRequest(input: SQSEvent, context: Context): Unit = {
    System.out.println("Handling request")

    val messages = input.getRecords.asScala.toList
    System.out.println(s"Received ${messages.length} messages")
    System.out.println(s"Received the message - ${messages}")
    messages.foreach { message =>
      val rawBody = message.getBody
      System.out.println(s"Processing event: $rawBody")
      val maybeRefundInput = circeDecode[APIGatewayEvent](rawBody)
      System.out.println(s"maybeRefundInput - ${maybeRefundInput}")
      maybeRefundInput match {
        case Right(refundInput) => processRequest(refundInput, context)
        case Left(ex) =>
          context.getLogger.log(s"Error '$ex' when decoding JSON to RefundInput with body: ${rawBody}")
      }
    }
  }

  def processRequest(input: APIGatewayEvent, context: Context): APIGatewayProxyResponseEvent = {
    input.body
      .pipe {
        read[RefundInput](_)
      }
      .tap {
        info[RefundInput]
      }
      .pipe {
        program
      }
      .tap {
        info[RefundOutput]
      }
      .pipe { output => okResponse(write(output)) }
  }

}

