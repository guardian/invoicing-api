package com.gu.invoicing.preview

import java.time.{DayOfWeek, LocalDate}
import com.gu.invoicing.preview.Model._
import com.gu.invoicing.preview.Impl._
import scala.io.Source

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
      0.0,
      "aChargeId"
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
      0.0,
      "aChargeId"
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
      0.0,
      "aChargeId"
    )
    assertEquals(pricePerPublication(item), expected = 2.04)
  }

  test("Publication price should take into account any percentage discounts") {
    val raw =
      Source.fromResource("preview/apply-discount-to-publication-price.json").getLines().mkString
    val publication = Publication(
      publicationDate = LocalDate.parse("2021-01-08"),
      LocalDate.parse("2021-01-08"),
      LocalDate.parse("2022-01-08"),
      "Guardian Weekly - Domestic",
      "GW Oct 18 - Annual - Domestic",
      DayOfWeek.FRIDAY,
      price = 5.77,
      ""
    )
    val allRatePlanCharges = read[Subscription](raw).ratePlans.flatMap(_.ratePlanCharges)
    val actualDiscountedPrice = applyAnyDiscounts(allRatePlanCharges, publication).price
    assertEquals(actualDiscountedPrice, expected = 5.2)
  }

  test("Publication price should not change if there are no percentage discounts") {
    val raw = Source
      .fromResource("preview/no-percentage-discount-should-not-change-price.json")
      .getLines()
      .mkString
    val publication = Publication(
      publicationDate = LocalDate.parse("2021-01-08"),
      LocalDate.parse("2021-01-08"),
      LocalDate.parse("2022-01-08"),
      "Guardian Weekly - Domestic",
      "GW Oct 18 - Annual - Domestic",
      DayOfWeek.FRIDAY,
      price = 5.77,
      ""
    )
    val allRatePlanCharges = read[Subscription](raw).ratePlans.flatMap(_.ratePlanCharges)
    val actualDiscountedPrice = applyAnyDiscounts(allRatePlanCharges, publication).price
    assertEquals(actualDiscountedPrice, expected = 5.77)
  }
}
