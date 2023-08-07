package com.gu.invoicing.refund

import com.gu.invoicing.refund.Model.RefundInput
import Model._

class InputSerialisationSuite extends munit.FunSuite {
  test("Serialisation works when adjustInvoices parameter is omitted") {
    val inputString = """{"subscriptionName": "A-S00045160","refund": 0.1}"""
    assertEquals(read[RefundInput](inputString).adjustInvoices, true)
  }

  test("Serialisation works when adjustInvoices parameter is present") {
    val inputString =
      """{"subscriptionName": "A-S00045160","refund": 0.1, "adjustInvoices": false}"""
    assertEquals(read[RefundInput](inputString).adjustInvoices, false)
  }

  test("Serialisation works for InvoiceItems") {
    val inputString = """{
                        |    "size": 1,
                        |    "records": [
                        |        {
                        |            "ChargeDate": "2023-01-30T14:13:11.000+00:00",
                        |            "UnitPrice": 12,
                        |            "SubscriptionNumber": "A-S00484373",
                        |            "ProductName": "Supporter Plus",
                        |            "ServiceEndDate": "2023-02-24",
                        |            "ServiceStartDate": "2023-01-25",
                        |            "ChargeName": "Supporter Plus Monthly",
                        |            "Id": "8ad081c686023f540186030654b778b7",
                        |            "InvoiceId": "8ad081c686023f540186030654ac78b6",
                        |            "ChargeAmount": 10.91,
                        |            "TaxAmount": 1.09,
                        |            "ChargeNumber": "C-00806529"
                        |        }
                        |    ],
                        |    "done": true
                        |}""".stripMargin
    assertEquals(read[InvoiceItemQueryResult](inputString).records.head.UnitPrice, BigDecimal(12))
  }
}
