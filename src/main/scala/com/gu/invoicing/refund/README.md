
## Refund

### Example request

Request

```http
POST /CODE/refund
Host: <API ID>.execute-api.eu-west-1.amazonaws.com
Content-Type: application/json

{
    "subscriptionName": "A-S00045160",
    "refund": 0.69
}
```

By default invoicing-api will adjust the invoices it applies refunds to. In some cases this might not be
what you want in which case you can pass the `adjustInvoices=false` parameter to prevent this.
[See this PR](https://github.com/guardian/invoicing-api/pull/146) for more details
eg.
```http
POST /CODE/refund
Host: <API ID>.execute-api.eu-west-1.amazonaws.com
Content-Type: application/json

{
    "subscriptionName": "A-S00045160",
    "refund": 0.69,
    "adjustInvoices": false
}
```

Response

```
{
    "subscriptionName": "A-S00045160",
    "refundAmount": 0.69,
    "invoiceId": "2c92c0f965562d5d01655716311d2b56",
    "paymentId": "2c92c095713ac30501713f23f49f05f5",
    "adjustments": [
        {
            "AdjustmentDate": "2020-04-14",
            "Amount": 0.69,
            "Comments": "a5e7945d-98f3-4b44-9537-64a25ca91f4d",
            "InvoiceId": "2c92c0f965562d5d01655716311d2b56",
            "Type": "Credit",
            "SourceType": "InvoiceDetail",
            "SourceId": "2c92c0f965562d5d0165571631292b6b"
        }
    ],
    "guid": "a5e7945d-98f3-4b44-9537-64a25ca91f4d"
}
```
By default invoicing-api will adjust the invoices it applies refunds to. In some cases this might not be 
what you want in which case you can pass the `adjustInvoices=false` parameter to prevent this.
[See this PR](https://github.com/guardian/invoicing-api/pull/146) for more details  

### How to manually apply refund

1. Invoices can be accessed from the customer account associated with the subscription
1. Take note of `Account Balance` before any changes
![image](https://user-images.githubusercontent.com/13835317/80216921-bdddb100-8636-11ea-88ba-71658b593cc1.png)
1. Find relevant invoice and click on the associated payment at the bottom under `Transaction Number`
![image](https://user-images.githubusercontent.com/13835317/80217340-6e4bb500-8637-11ea-8cd8-f3c678c1c90c.png)
1. `more | Refund this payment`
1. Heed the warning which says the process has **two** steps: we must **adjust** the invoice after creating a refund
![image](https://user-images.githubusercontent.com/13835317/80217571-d7332d00-8637-11ea-921e-b1db25c9117f.png)
1. Specify the `Refund Amount` and click `create refund and adjust invoice items`
1. **WARNING**: If you create the refund but do not adjust the invoice items, then the refund will be applied, however
on the next invoice run the same amount will be re-collected which effectively means no refund was applied.
1. Spread the refund amount across (potentially multiple) invoice items by adding a value inside `Credit` field
![image](https://user-images.githubusercontent.com/13835317/80218522-30e82700-8639-11ea-8e40-206933e5f105.png)
1. Click `adjust invoice items` 
1. Make sure `Account Balance` from step 2 has remained unchanged. If there is a difference, then you have done something
wrong and that amount will be re-collected on the next payment run.

### How to fix a failed automated refund attempt

Urgent alarm will be emailed to fulfilment.dev which needs to be actioned like so:

1. First familiarise yourself with above section explaining how to manually apply refund
1. AWS logs should indicate the exact failing runtime assertion. Because of the inlined design of the implementation this 
should exactly indicate which steps and corresponding system mutations have happened.
1. If no refund object has been created then there is nothing to cleanup. Follow tha above manual process from beginning.
1. If refund object has been created, but the corresponding adjustments were not made, then start [manual process](#-how-to-manually-apply-refund) from
the warning in step 7.
1. If account balance after refund+adjustments were made is not equal to the account balance before the process, then 
the program calculated wrong adjustments. Apply additional adjustments by following [manual process](#-how-to-manually-apply-refund) from the 
warning in step 7.

### How to test in CODE

1. For faster feedback cycle skip riffraff by assembling and deploying lambda directly
1. Import Janus credentials 
1. Execute `sbt "deployAwsLambda CODE"` which should update `invoicing-api-refund-CODE`
1. Use postman to hit the corresponding endpoint

