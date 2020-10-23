## Preview affected publications

Uses [operations/billing-preview](https://www.zuora.com/developer/api-reference/#operation/POST_BillingPreview) 
to determine *next invoice date*. The following are all equivalent interpretation of the next invoice date
 - day after last invoiced period
 - day after last service period
 - day after `serviceEndDate`
 - `chargedThroughDate` of last invoiced period
 - first day of next invoiced period

**Request:**

```
GET /next-invoice-date/{subscriptionName}
Host: https://{apiGatewayId}.execute-api.{region}.amazonaws.com/{STAGE}
x-api-key: ********
```

**Response:**

If the date exists

```
{
  "nextInvoiceDate": "2020-10-27"
}
```

otherwise 

```
{}
```

### How to test in CODE

#### With CLI 

1. Get invoicing-api+uat@guardian.co.uk Zuora OAuth client credentials
1. Follow docs in `Cli.scala` and create `Stage` and `Config` environmental variables
1. Simply run something like
    ```
    program(NextInvoiceDateInput("A-S00000000"))
    ```

#### With Lambda

1. Get fresh Janus credentials
1. `Program.scala` contains main business logic 
1. `deployAwsLambda CODE` will upload modified lambda package
1. In AWS Lambda console create test event that reflects `GET /next-invoice-date/{subscriptionName}`
    ```
    {
      "pathParameters": {
        "subscriptionName": "A-S00000000"
      }
    }
    ```
1. Tail logs 
    ```
    export AWS_DEFAULT_REGION=eu-west-1 && awslogs get --profile membership /aws/lambda/invoicing-api-nextinvoicedate-CODE --watch
    ```
