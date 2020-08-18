package com.gu.invoicing.common

import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatter.ofPattern
import java.time.{LocalDate, LocalDateTime}

class JsonSupport extends upickle.AttributeTagged {
  /* Option - http://www.lihaoyi.com/upickle/#CustomConfiguration */
  implicit def optionWriter[T: Writer]: Writer[Option[T]] =
    implicitly[Writer[T]].comap[Option[T]] {
      case None => null.asInstanceOf[T]
      case Some(x) => x
    }
  implicit def optionReader[T: Reader]: Reader[Option[T]] = {
    new Reader.Delegate[Any, Option[T]](implicitly[Reader[T]].map(Some(_))){
      override def visitNull(index: Int) = None
    }
  }

  /* Dates */
  implicit val bigDecimalRW: ReadWriter[BigDecimal] = readwriter[Double].bimap[BigDecimal](_.toDouble, double => BigDecimal(double.toString))
  implicit val localDateRW: ReadWriter[LocalDate] = readwriter[String].bimap[LocalDate](_.toString, LocalDate.parse(_, ofPattern("yyyy-MM-dd")))
  implicit val localDateTimeRW: ReadWriter[LocalDateTime] = readwriter[String].bimap[LocalDateTime](_.toString, LocalDateTime.parse(_, DateTimeFormatter.ISO_OFFSET_DATE_TIME)) // ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

  /* Logging json */
  def info[P <: Product : Writer](p: P): Unit = info(write(p))
  def warn[P <: Product : Writer](p: P): Unit = warn(write(p))
  def error[P <: Product : Writer](p: P): Unit = error(write(p))
  private val logger = java.util.logging.Logger.getGlobal
  private object ErrorLevel extends java.util.logging.Level("ERROR", 950)
  private def info(s: String): Unit = logger.info(s)
  private def warn(s: String): Unit = logger.warning(s)
  private def error(s: String): Unit = logger.log(ErrorLevel, s)
}