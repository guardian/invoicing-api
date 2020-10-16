package com.gu.invoicing.nextinvoicedate

import java.time.LocalDate

import com.gu.invoicing.nextinvoicedate.Model.InvoiceItem
import com.gu.invoicing.nextinvoicedate.Impl.findNextInvoiceDate

class NextInvoiceDateSuite extends munit.FunSuite {
  val a = InvoiceItem(
    "12345qwerty",
    "A-S00000000",
    LocalDate.parse("2020-09-27"),
    LocalDate.parse("2020-10-26"),
    11.99
  )
  val b = InvoiceItem(
    "12345qwerty",
    "A-S00000000",
    LocalDate.parse("2020-10-27"),
    LocalDate.parse("2020-11-26"),
    11.99
  )
  val c = InvoiceItem(
    "12345qwerty",
    "A-S00000000",
    LocalDate.parse("2020-11-27"),
    LocalDate.parse("2020-12-26"),
    11.99
  )

  test("Next invoice date should be first day after the current invoice service period") {
    val actual = findNextInvoiceDate(List(a,b,c), LocalDate.parse("2020-10-16"))
    assertEquals(actual.get, expected = b.serviceStartDate)
  }
  test("Next invoice date should be first day after the current invoice service period (today is left bound)") {
    val actual = findNextInvoiceDate(List(a,b,c), LocalDate.parse("2020-09-27"))
    assertEquals(actual.get, expected = b.serviceStartDate)
  }
  test("Next invoice date should be first day after the current invoice service period (today is right bound)") {
    val actual = findNextInvoiceDate(List(a,b,c), LocalDate.parse("2020-09-26"))
    assertEquals(actual.get, expected = b.serviceStartDate)
  }
  test("Next invoice date should not be determined if there is no next service period") {
    val actual = findNextInvoiceDate(List(a), LocalDate.parse("2020-10-16"))
    assertEquals(actual, expected = None)
  }
  test("Next invoice date should not be determined if there are no invoice items") {
    val actual = findNextInvoiceDate(List(a), LocalDate.parse("2020-10-16"))
    assertEquals(actual, expected = None)
  }
}
