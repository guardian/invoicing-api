# Refund Erroneous Payment

This is for refunding in cases where a payment was inadvertently taken for an invoice.
This could be, for instance, when a cancelled sub has been left with an amount outstanding
and then a retry attempt is made to collect the outstanding amount.

### Example request

Request

```http
POST /CODE/refund-erroneous-payment
Host: <API ID>.execute-api.eu-west-1.amazonaws.com
Content-Type: application/json

{
    "accountId": "A-S12345678",
    "invoiceNumber": "INV123456",
    "paymentId": "p123",
    "invoiceAmount": 0.69,
    "comment": "???"
}
```

Response

```
{
    "accountId": "A123",
    "paymentId": "P123",
    "invoiceAmount": 0.69,
    "invoiceNumber": "INV123456",
    "balancingInvoiceNumber": "INV654321"
}
```

### How to manually apply refund

TODO
