# invoicing-api

Invoice management for supporters

## Configuration

* Riff-Raff project: `support:invoicing-api`
* Artifact bucket: `membership-dist/support/PROD/invoicing-api/invoicing-api.jar`
* Zuora API User: `invoicing-api+uat@guardian.co.uk`
* Parameter store: `/invoicing-api/${Stage}/config`

## Structure

| Project                                                     | Description                                  |                                    
| ----------------------------------------------------------- | -------------------------------------------- |
| [refund](src/main/scala/com/gu/invoicing/refund)            | Apply automated refund                       |
| [invoices](src/main/scala/com/gu/invoicing/invoice)         | List all invoices by identityId              |
| [pdf](src/main/scala/com/gu/invoicing/pdf)                  | Download PDF invoice                         |