package com.gu.invoicing.refund

import com.gu.invoicing.refund.Model._

import scala.io.Source

class SpreadRefundAcrossItemsSpec extends munit.FunSuite {
  test("spreadRefundAcrossItems function works correctly") {
    val response = Source.fromResource("refund/invoice-items.json").getLines().mkString
    val items = read[InvoiceItemQueryResult](response).records

    val adjustments = Impl.spreadRefundAcrossItems(items, Nil, Nil, 14.42, "xxxxxxx")

    assertEquals(adjustments.length, 2)
    assertEquals(adjustments.map(_.Amount).sum, BigDecimal(14.42))
  }
}