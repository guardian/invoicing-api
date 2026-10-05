# Invoicing API CDK Infrastructure

This directory contains the AWS CDK infrastructure for the Invoicing API service, which manages Zuora invoice operations including refunds, invoice retrieval, PDF downloads, and invoice previews.

## Architecture

The infrastructure consists of:
- **Multiple Lambda Functions**:
  - `refund` - Apply refund to Zuora invoice by adjusting invoice items
  - `invoices` - Retrieve all invoices with payment methods by accountId
  - `pdf` - Download PDF invoice by invoiceId  
  - `nextinvoicedate` - Get next invoice date (day after last invoice period)
  - `preview` - Preview affected publications within date range
  - `refund-erroneous-payment` - Apply refund by shuffling credit balances
- **API Gateway** with custom domain and API key authentication
- **CloudWatch Alarms** for monitoring (PROD only)
- **IAM Roles** with appropriate permissions for S3 and Parameter Store access
- **Route53 DNS** records for custom domain

## Migration from CloudFormation

This CDK application replaces the original `cfn.yaml` CloudFormation template. The CDK version provides:
- Type-safe infrastructure definitions
- Better integration with Guardian CDK patterns
- Improved testing and validation capabilities
- Consistent deployment patterns

## Deployment

### Prerequisites

1. Install dependencies:
   ```bash
   npm install
   ```

2. Configure AWS credentials for the membership account

3. Bootstrap CDK (first time only):
   ```bash
   npm run bootstrap
   ```

### Deploy to CODE environment

```bash
npm run deploy:code
```

### Deploy to PROD environment

```bash
npm run deploy:prod
```

### Other useful commands

- `npm run build` - compile TypeScript to JavaScript
- `npm run watch` - watch for changes and compile
- `npm run test` - perform the Jest unit tests
- `npm run lint` - run ESLint
- `npm run synth` - synthesize CloudFormation template
- `npm run diff` - compare deployed stack with current state
- `npm run destroy` - delete the stack

## Configuration

The stack deploys to different environments based on the `stage` context parameter:
- `CODE` - Development environment with domain `invoicing-api-code.support.guardianapis.com`
- `PROD` - Production environment with domain `invoicing-api.support.guardianapis.com`

## API Endpoints

- `POST /refund` - Apply refund to invoice
- `POST /refund-erroneous-payment` - Apply refund by credit shuffle
- `GET /invoices` - List invoices for account (IAM auth)
- `GET /invoices/{invoiceId}` - Download PDF invoice (IAM auth)
- `GET /next-invoice-date/{subscriptionName}` - Get next invoice date
- `GET /preview/{subscriptionName}?startDate=&endDate=` - Preview affected publications

## Monitoring

The stack includes CloudWatch alarms (PROD only) that monitor:
- Failed invoice fetches
- PDF download failures
- Next invoice date calculation errors
- Preview operation failures

## Dependencies

- **S3 Buckets**: 
  - `membership-dist` - deployment artifacts
  - `gu-reader-revenue-private` - Zuora credentials
- **Parameter Store**: `/invoicing-api/${stage}/config` - application configuration
- **Certificate**: Uses existing *.support.guardianapis.com certificate
- **Hosted Zone**: Uses existing support.guardianapis.com zone

## Lambda Configuration

All Lambda functions use:
- Java 11 runtime
- 3008 MB memory
- 15 minute timeout
- Common IAM role with S3 and logging permissions
- Configuration from Parameter Store
