package com.gu.invoicing.common

import scala.annotation.tailrec
import scala.util.{Failure, Try}

object Retry { // https://stackoverflow.com/a/7931459/5205022
  @tailrec
  def retry[T](n: Int)(fn: => T): Try[T] = {
    Try { fn } match {
      case Failure(_) if n > 1 => retry(n - 1)(fn)
      case fn => fn
    }
  }
  def retry[T](fn: => T): Try[T] = retry(2)(fn)
}
