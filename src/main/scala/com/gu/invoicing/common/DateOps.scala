package com.gu.invoicing.common

import java.time.LocalDate

object DateOps {
  implicit class LocalDateOps(private val a: LocalDate) extends AnyVal {
    def >=(b: LocalDate): Boolean = a.isEqual(b) || a.isAfter(b)
    def <=(b: LocalDate): Boolean = a.isEqual(b) || a.isBefore(b)
    def inClosedInterval(start: LocalDate, end: LocalDate): Boolean = (a >= start) && (a <= end)
  }
}
