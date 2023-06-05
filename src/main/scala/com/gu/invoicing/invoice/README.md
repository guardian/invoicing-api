## Get all invoices by identityId

- Identity is provided by `x-identity-id` header
- The request requires api key as well as AWS Signature. Example client manage-frontend PR: https://github.com/guardian/manage-frontend/pull/465

**Request:**

```
GET /invoices
Host: https://{apiGatewayId}.execute-api.{region}.amazonaws.com/{STAGE}
Content-Type: application/json
x-identity-id: 123456
x-api-key: ********
X-Amz-Security-Token: ******** 
X-Amz-Date: ********
Authorization: ******** 
```

**Response:**

```
{
    "invoices": [
        {
            "invoiceId": "1234567899qwertyuio",
            "subscriptionName": "A-S0000000",
            "date": "2020-08-05",
            "pdfPath": "invoices/afileId",
            "price": 813.48,
            "paymentMethod": "BankTransfer",
            "last4": "9911"
        }
    ]
}
```

### How to test in CODE

#### With CLI 

1. Get invoicing-api+dev@guardian.co.uk Zuora OAuth client credentials
1. Follow docs in `Cli.scala` and create `Stage` and `Config` environmental variables
1. Performance can be estimated with something like 
    ```
    time { Await.result(program(InvoicesInput("someAccountId")), Inf) }
    ```

#### With Lambda

1. Get fresh Janus credentials
1. `Program.scala` contains main business logic 
1. `deployAwsLambda CODE` will upload modified lambda package
1. In AWS Lambda console create test event with reflects `GET /invoices/{accountId}`
    ```
    {
      "headers": {
        "x-identity-id": "123456"
      }
    }
    ```
1. Tail logs 
    ```
    export AWS_DEFAULT_REGION=eu-west-1 && awslogs get --profile membership /aws/lambda/invoicing-api-invoices-CODE --watch
    ```
   
#### With API Gateway (Postman)

1. HTTP request will have to be signed with AWS signature
1. Get fresh janus credentials
1. Paste them in `Postman | Auth | AWS Signature`
1. Don't forget also the API key `x-api-key`
