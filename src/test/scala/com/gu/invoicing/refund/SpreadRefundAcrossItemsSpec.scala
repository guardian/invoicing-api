package com.gu.invoicing.refund

import com.gu.invoicing.refund.Model._

import java.time.{LocalDate, ZoneId}
import scala.io.Source

class SpreadRefundAcrossItemsSpec extends munit.FunSuite {
  test("spreadRefundAcrossItems function works correctly with invoices with negative charges") {
    val invoices = read[InvoiceQueryResult](
      Source.fromResource("refund/invoices.json").getLines().mkString,
    ).records

    val invoiceItems = read[InvoiceItemQueryResult](
      Source.fromResource("refund/invoice-items.json").getLines().mkString,
    ).records

    val (invoiceId, _, relevantInvoiceItems) =
      Impl.decideRelevantInvoice(14.42, invoices, invoiceItems.groupBy(_.InvoiceId))

    val adjustments = Impl.spreadRefundAcrossItems(
      relevantInvoiceItems,
      Nil,
      Nil,
      14.42,
      "xxxxxxx",
    )

    assertEquals(invoiceId, "8ad08d2989d472170189da366b940c8b")
    assertEquals(adjustments.length, 2)
    assertEquals(adjustments.map(_.Amount).sum, BigDecimal(14.42))
  }
  test("spreadRefundAcrossItems function should use the correct adjustment date") {
    val invoices = read[InvoiceQueryResult](
      Source.fromResource("refund/invoices.json").getLines().mkString,
    ).records

    val invoiceItems = read[InvoiceItemQueryResult](
      Source.fromResource("refund/invoice-items.json").getLines().mkString,
    ).records

    val (invoiceId, _, relevantInvoiceItems) =
      Impl.decideRelevantInvoice(14.42, invoices, invoiceItems.groupBy(_.InvoiceId))

    val adjustments = Impl.spreadRefundAcrossItems(
      relevantInvoiceItems,
      Nil,
      Nil,
      14.42,
      "xxxxxxx",
    )
    val expectedAdjustmentDate = LocalDate.now(ZoneId.of("Europe/London"))
    assertEquals(adjustments.head.AdjustmentDate, expectedAdjustmentDate)
  }
  test("spreadRefundAcrossItems function should work when there is more than one invoice on the same day") {
    val refundAmount = BigDecimal(70)
    val invoices = read[InvoiceQueryResult](
      Source.fromResource("refund/invoices-2.json").getLines().mkString,
    ).records

    val invoiceItems = read[InvoiceItemQueryResult](
      Source.fromResource("refund/invoice-items-2.json").getLines().mkString,
    ).records

    val (invoiceId, _, relevantInvoiceItems) =
      Impl.decideRelevantInvoice(refundAmount, invoices, invoiceItems.groupBy(_.InvoiceId))

    val adjustments = Impl.spreadRefundAcrossItems(
      relevantInvoiceItems,
      Nil,
      Nil,
      refundAmount,
      "xxxxxxx",
    )

    assertEquals(adjustments.head.Amount, refundAmount)
  }
  test("availableAmount function works correctly for invoice items with negative charge") {
    val negativeInvoiceItem = read[InvoiceItem](
      """
        |{
        |      "ChargeDate": "2023-08-09T13:12:26.000+01:00",
        |      "TaxAmount": 0,
        |      "SubscriptionNumber": "A-S00475254",
        |      "ProductName": "Contributor",
        |      "ServiceEndDate": "2023-08-10",
        |      "ServiceStartDate": "2023-08-09",
        |      "ChargeName": "Contribution",
        |      "Id": "8ad08d2989d472170189da366b9f0c8d",
        |      "InvoiceId": "8ad08d2989d472170189da366b940c8b",
        |      "ChargeAmount": -0.58,
        |      "ChargeNumber": "C-00791025"
        |    }
        |""".stripMargin,
    )
    assertEquals(Impl.availableAmount(negativeInvoiceItem, Nil), None)
  }
}
