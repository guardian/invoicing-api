package com.gu.invoicing.invoice

import Model._
/**
 * Log deserialised JSON objects.
 */
object Log extends {
  def info[P <: Product : Writer](p: P): Unit = info(write(p))
  def warn[P <: Product : Writer](p: P): Unit = warn(write(p))
  def error[P <: Product : Writer](p: P): Unit = error(write(p))

  private val logger = java.util.logging.Logger.getGlobal
  private object ErrorLevel extends java.util.logging.Level("ERROR", 950)
  private def info(s: String): Unit = logger.info(s)
  private def warn(s: String): Unit = logger.warning(s)
  private def error(s: String): Unit = logger.log(ErrorLevel, s)
}
