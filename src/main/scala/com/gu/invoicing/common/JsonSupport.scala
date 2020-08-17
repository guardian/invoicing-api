package com.gu.invoicing.common

/**
 * Needed for upickle to handle optional fields
 * http://www.lihaoyi.com/upickle/#CustomConfiguration
 */
class JsonSupport extends upickle.AttributeTagged {
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

  // Log json
  def info[P <: Product : Writer](p: P): Unit = info(write(p))
  def warn[P <: Product : Writer](p: P): Unit = warn(write(p))
  def error[P <: Product : Writer](p: P): Unit = error(write(p))
  private val logger = java.util.logging.Logger.getGlobal
  private object ErrorLevel extends java.util.logging.Level("ERROR", 950)
  private def info(s: String): Unit = logger.info(s)
  private def warn(s: String): Unit = logger.warning(s)
  private def error(s: String): Unit = logger.log(ErrorLevel, s)
}