package com.gu.invoicing.refund

import com.gu.invoicing.refund.Model.RefundInput
import Model._

class InputSerialisationSuite extends munit.FunSuite {
  test("Serialisation works when adjustInvoices parameter is omitted") {
    val inputString = """{"subscriptionName": "A-S00045160","refund": 0.1}"""
    assertEquals(read[RefundInput](inputString).adjustInvoices, true)
  }

  test("Serialisation works when adjustInvoices parameter is present") {
    val inputString = """{"subscriptionName": "A-S00045160","refund": 0.1, "adjustInvoices": false}"""
    assertEquals(read[RefundInput](inputString).adjustInvoices, false)
  }
}
