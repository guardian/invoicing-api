## Preview affected paper publications within a range

This endpoint breaks `InvoiceItem`s into individual `Publication`s and provides the following answers
* the date of the publication
* the price of the publication
* when was the publication invoiced
* when would the publication be credited
* the day of the week of the publication

The way it works is that it combines past `InvoiceItem`s (using `transactions/invoices/accounts/$account"`) with 
future `InvoiceItem`s (using [operations/billing-preview](https://www.zuora.com/developer/api-reference/#operation/POST_BillingPreview)) 
and then breaks them up into particular publications. `Publication` is of higher granularity than `InvoiceItem` and
represents the smallest unit of a product, that is, one particular paper published on a particular day in a week.

Note `Publication` represents a physical `paper` (as opposed to digital products or contributions).

This endpoint can be used, for example, by holiday-stop-api to predict affected publications which need to be 
suspended from fulfilment and credited in next invoice.


**Request:**

```
GET /preview/A-S00000000?startDate=2020-11-13&endDate=2020-12-25 HTTP/1.1
Host: https://{apiGatewayId}.execute-api.{region}.amazonaws.com/{stage}
Content-Type: application/json
x-api-key: ************
```

**Response:**

If the date exists

```
{
    "subscriptionName": "A-S00000000",
    "nextInvoiceDateAfterToday": "2020-12-04",
    "rangeStartDate": "2020-11-13",
    "rangeEndDate": "2020-12-25",
    "publicationsWithinRange": [
        {
            "publicationDate": "2020-11-13",
            "invoiceDate": "2020-11-04",
            "nextInvoiceDate": "2020-12-04",
            "productName": "Newspaper Delivery",
            "chargeName": "Friday",
            "dayOfWeek": "FRIDAY",
            "price": 2.04
        },
        ...
   ]
}
```

otherwise 

```
{
    "subscriptionName": "A-S00000000",
    "nextInvoiceDateAfterToday": "2020-12-04",
    "rangeStartDate": "2020-11-13",
    "rangeEndDate": "2020-12-25",
}
```

### How to test in CODE

#### With CLI 

1. Get invoicing-api+dev@guardian.co.uk Zuora OAuth client credentials
1. Follow docs in `Cli.scala` and create `Stage` and `Config` environmental variables
1. Simply run something like
    ```
    program(PreviewInput("A-S00000000", LocalDate.parse("2020-11-13"), LocalDate.parse("2020-12-25")))
    ```

#### With Lambda

1. Get fresh Janus credentials
1. `Program.scala` contains main business logic 
1. `deployAwsLambda CODE` will upload modified lambda package
1. In AWS Lambda console create test event that reflects `GET /preview/A-S00000000?startDate=2020-11-13&endDate=2020-12-25`
    ```
     {
       "pathParameters": {
         "subscriptionNumber": "A-S00000000"
       },
       "queryStringParameters": {
         "startDate": "2020-10-10",
         "endDate": "2020-10-20"
       }
     }
    ```
1. Tail logs 
    ```
    export AWS_DEFAULT_REGION=eu-west-1 && awslogs get --profile membership /aws/lambda/invoicing-api-preview-CODE --watch
    ```
