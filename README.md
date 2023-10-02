# invoicing-api

Invoice management for supporters

## Configuration

* Riff-Raff project: `support:invoicing-api`
* Artifact bucket: `membership-dist/support/PROD/invoicing-api/invoicing-api.jar`
* Zuora API User: `invoicing-api+dev@guardian.co.uk`
* Parameter store: `/invoicing-api/${Stage}/config` - After making any changes to the config, you will need to update the cloudformation to update the lambda's environment variables function with the new config.  The update needed is around these lines: https://github.com/guardian/invoicing-api/blob/cfcbdd44ff8f5b4e3d92d82e1feace979741a522/cfn.yaml#L16-L19

## Structure

| Project                                                           | Description                                     |                                    
| ----------------------------------------------------------------- | ----------------------------------------------- |
| [refund](src/main/scala/com/gu/invoicing/refund)                  | Apply automated refund                          |
| [invoices](src/main/scala/com/gu/invoicing/invoice)               | List all invoices by identityId                 |
| [pdf](src/main/scala/com/gu/invoicing/pdf)                        | Download PDF invoice                            |
| [nextinvoicedate](src/main/scala/com/gu/invoicing/nextinvoicedate)| First day of the next billing period            |
| [preview](src/main/scala/com/gu/invoicing/preview)                | Publications impacted in a given date range     |
