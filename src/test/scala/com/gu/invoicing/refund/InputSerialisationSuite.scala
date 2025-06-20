package com.gu.invoicing.refund

import com.gu.invoicing.refund.Model.{RefundInput, _}

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
    assertEquals(read[InvoiceItemQueryResult](inputString).records.head.amountWithTax, BigDecimal(12))
  }

  test("can deserialise a payment response") {
    val testData = TestData.paymentResponse
    val actual = read[Payment](testData)
    val expected = Payment(PaymentStatus.Processed)
    assertEquals(actual, expected)
  }
}

object TestData {
  val paymentResponse =
    """{
      |"accountId": "4028905f5a87c0ff015a87d25ae90025",
      |"accountNumber": "A00000001",
      |"amount": 44.1,
      |"appliedAmount": 44.1,
      |"authTransactionId": null,
      |"bankIdentificationNumber": null,
      |"cancelledOn": null,
      |"comment": "normal payment",
      |"createdById": "402881e522cf4f9b0122cf5d82860002",
      |"createdDate": "2017-03-01 11:30:37",
      |"creditBalanceAmount": 0,
      |"currency": "USD",
      |"effectiveDate": "2017-03-01",
      |"financeInformation": {
      |"bankAccountAccountingCode": null,
      |"bankAccountAccountingCodeType": null,
      |"transferredToAccounting": "No",
      |"unappliedPaymentAccountingCode": null,
      |"unappliedPaymentAccountingCodeType": null
      |},
      |"gatewayId": null,
      |"gatewayOrderId": null,
      |"gatewayReconciliationReason": null,
      |"gatewayReconciliationStatus": null,
      |"gatewayResponse": null,
      |"gatewayResponseCode": null,
      |"gatewayState": "NotSubmitted",
      |"id": "4028905f5a87c0ff015a87eb6b75007f",
      |"markedForSubmissionOn": null,
      |"number": "P-00000001",
      |"paymentGatewayNumber": "PG-00000001",
      |"paymentMethodId": "402881e522cf4f9b0122cf5dc4020045",
      |"paymentMethodSnapshotId": null,
      |"payoutId": null,
      |"referenceId": null,
      |"refundAmount": 0,
      |"secondPaymentReferenceId": null,
      |"settledOn": null,
      |"softDescriptor": null,
      |"softDescriptorPhone": null,
      |"status": "Processed",
      |"submittedOn": null,
      |"success": true,
      |"type": "External",
      |"unappliedAmount": 0,
      |"updatedById": "402881e522cf4f9b0122cf5d82860002",
      |"updatedDate": "2017-03-01 11:30:37"
      |}""".stripMargin
}