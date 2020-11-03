package com.gu.invoicing.preview

import java.time.LocalDate
import com.gu.invoicing.preview.Model._
import com.gu.invoicing.preview.Impl._

class PricePerPublicationSuite extends munit.FunSuite {
  test("Guardian Weekly 6 for 6 per publication price should be predicted") {
    val item = InvoiceItem(
      "12345qwerty",
      "A-S00000000",
      LocalDate.parse("2020-11-13"),
      LocalDate.parse("2020-12-24"),
      6.000000000,
      "Guardian Weekly - Domestic",
      "GW Oct 18 - First 6 issues - Domestic",
    )
    assertEquals(pricePerPublication(item), expected = 1.0)
  }
  test("Guardian Weekly quarterly per publication price should be predicted") {
    val item = InvoiceItem(
      "12345qwerty",
      "A-S00000000",
      LocalDate.parse("2020-12-25"),
      LocalDate.parse("2021-03-24"),
      37.500000000,
      "Guardian Weekly - Domestic",
      "GW Oct 18 - Quarterly - Domestic",
    )
    assertEquals(pricePerPublication(item), expected = 2.89)
  }
  test("Home Delivery monthly per publication price should be predicted") {
    val item = InvoiceItem(
      "12345qwerty",
      "A-S00000000",
      LocalDate.parse("2021-02-04"),
      LocalDate.parse("2021-03-03"),
      8.16,
      "Newspaper Delivery",
      "Friday",
    )
    assertEquals(pricePerPublication(item), expected = 2.04)
  }
}
