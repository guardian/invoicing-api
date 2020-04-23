package com.gu.invoicing.refund

object Log extends {
  private val logger = java.util.logging.Logger.getGlobal
  object ErrorLevel extends java.util.logging.Level("ERROR", 950)
  def info(s: String): Unit = logger.info(s)
  def warn(s: String): Unit = logger.warning(s)
  def error(s: String): Unit = logger.log(ErrorLevel, s)
}
