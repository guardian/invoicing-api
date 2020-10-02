package com.gu.invoicing.common

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Try}

object Retry { // https://stackoverflow.com/a/7931459/5205022
  private final val NumOfRetries = 3
  @tailrec private def retry[T](n: Int)(fn: => T): Try[T] = {
    Try { fn } match {
      case Failure(_) if n > 1 => retry(n - 1)(fn)
      case fn => fn
    }
  }

  private def retry[T](op: => Future[T], retries: Int)(implicit ec: ExecutionContext): Future[T] =
    op recoverWith { case _ if retries > 0 => retry(op, retries - 1) }

  def retry[T](fn: => T): Try[T] = retry(NumOfRetries)(fn)
  def retryUnsafe[T](fn: => T): T = retry(fn).get
  def retry[T](fn: => Future[T])(implicit ec: ExecutionContext): Future[T] = retry(fn,NumOfRetries)
}
