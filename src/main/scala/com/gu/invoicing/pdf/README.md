## Get invoice PDF by invoiceId

The endpoint is secured with 
- API key
- AWS Signature
- Validation that invoice belongs to identity that requests it 

**Request:**

```
GET /invoices/{invoiceId}
Host: https://{apiGatewayId}.execute-api.{region}.amazonaws.com/{STAGE}
x-identity-id
x-api-key: ********
X-Amz-Security-Token: ******** 
X-Amz-Date: ********
Authorization: ******** 
```

**Response:**

Base64 encoded PDF:

```
Content-Type: application/pdf;charset=UTF-8
```

### How to test in CODE

#### With CLI 

1. Get invoicing-api+uat@guardian.co.uk Zuora OAuth client credentials
1. Follow docs in `Cli.scala` and create `Stage` and `Config` environmental variables
1. Simply run something like
    ```
    program(PdfInput("anInvoiceId", "anIdentityId"))
    ```

#### With Lambda

1. Get fresh Janus credentials
1. `Program.scala` contains main business logic 
1. `deployAwsLambda` will upload modified lambda package
1. In AWS Lambda console create test event that reflects `GET /invoices/{invoiceId}`
    ```
    {
      "headers": {
        "x-identity-id": "1000001"
      },
      "pathParameters": {
        "invoiceId": "1a2s3d4f5g6h7j8k"
      }
    }
    ```
1. Tail logs 
    ```
    export AWS_DEFAULT_REGION=eu-west-1 && awslogs get --profile membership /aws/lambda/invoicing-api-pdf-CODE --watch
    ```
   
#### With API Gateway (Postman)

1. HTTP request will have to be signed with AWS signature
1. Get fresh janus credentials
1. Paste them in `Postman | Auth | AWS Signature`
1. Don't forget also the API key `x-api-key`

### Configure PDF binary response in API Gateway

1. Add `application/pdf` under `Settings | Binary Media Types` and then re-deploy the API.
1. https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-payload-encodings-configure-with-console.html
1. https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-resource-apigateway-restapi.html#cfn-apigateway-restapi-binarymediatypes
