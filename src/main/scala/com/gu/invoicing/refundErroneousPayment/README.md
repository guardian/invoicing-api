# Refund Erroneous Payment

This is for refunding in cases where a payment was inadvertently taken for an invoice
where there is also a balancing negative invoice on the same account.
This could be, for instance, when a cancelled sub has been left with an amount outstanding
and then a retry attempt is made to collect the outstanding amount.
The script will ensure that there is no change in the invoice items or adjustments, meaning
that the finance revenue schedules will not be affected.

### Example request

Request

```http
POST /CODE/refund-erroneous-payment
Host: <API ID>.execute-api.eu-west-1.amazonaws.com
Content-Type: application/json

{
    "accountId": "A123",
    "paymentDate": "2022-01-01"
    "comment": "Reason why this is necessary"
}
```

Response

```
{
    "accountId": "A123",
    "refundData": [
      {
        "invoiceNumber": "INV123456",
        "invoiceAmount": 0.69,
        "paymentId": "P123",
        "refundId": "R123",
      },
      {
        "invoiceNumber": "INV654321",
        "invoiceAmount": 1.32,
        "paymentId": "P345",
        "refundId": "R456",
      }
    ],
    "balancingInvoiceNumber": "INV576849"
}
```

### How to manually apply refund

TODO
