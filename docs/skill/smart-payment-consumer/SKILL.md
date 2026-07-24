---
name: smart-payment-consumer
description: Integrate Smartverse backend services with smart-payment-backend to create InfinitePay payment links and consume confirmed-payment events through generated Gonthera RabbitMQ subscribers. Use when implementing payment requests, configuring payment-service queues, handling payment.confirmed events, granting a plan or resource after payment, or documenting the Smart Payment contract in CHURCH_LITE, FOUR_LIBERT, or REPORT.
---

# Smart Payment Consumer

Use this contract in services that request payment links or react to confirmed
payments. Generate RabbitMQ infrastructure through Gonthera. Do not duplicate the
payment provider integration in consumer services.

## Service contract

Use the public base URL:

```text
https://app.smartverse.com.br/api/payment-service
```

Treat `value` as an integer number of cents. Never send decimal currency values.

### Create or reuse a payment link

Call:

```http
POST /paymentLink
Authorization: Bearer <token>
Content-Type: application/json
```

Send:

```json
{
  "service": "CHURCH_LITE",
  "value": 1000,
  "client_id": "94cbeb99-56a7-49ce-ad13-c82e71ae25f1"
}
```

Rules:

- Set `service` to the requesting service.
- Set `value` to a positive amount in cents.
- Set `client_id` to the stable UUID that the consumer uses to identify the
  customer, subscription or account that will receive the benefit.
- Send the normal Smartverse authorization token.
- Do not create a local InfinitePay link directly.
- Reuse the returned link when `reused` is `true`.

Accepted services:

```text
CHURCH_LITE
FOUR_LIBERT
REPORT
```

Successful response:

```json
{
  "url": "https://checkout.infinitepay.com.br/...",
  "order_nsu": "client-uuid-CHURCH_LITE-payment-uuid",
  "status": "PENDING",
  "reused": false
}
```

Store `order_nsu` when the consumer needs local audit correlation. Do not treat a
redirect to the consumer as proof of payment. Grant the purchased benefit only
after consuming a confirmed-payment event.

## RabbitMQ subscription

Use the exchange:

```text
smart.payment.events
```

Register exactly one `sub` for the service being integrated.

### Church Lite

```json
{
  "name": "paymentConfirmed",
  "queue": "smart.payment.confirmed.church-lite"
}
```

Routing key:

```text
payment.confirmed.CHURCH_LITE
```

### 4Libert

```json
{
  "name": "paymentConfirmed",
  "queue": "smart.payment.confirmed.four-libert"
}
```

Routing key:

```text
payment.confirmed.FOUR_LIBERT
```

### Report

```json
{
  "name": "paymentConfirmed",
  "queue": "smart.payment.confirmed.report"
}
```

Routing key:

```text
payment.confirmed.REPORT
```

Place the selected channel inside `.gonthera/project.json`:

```json
{
  "messaging": {
    "RabbitMq": {
      "pub": [],
      "sub": [
        {
          "name": "paymentConfirmed",
          "queue": "smart.payment.confirmed.church-lite"
        }
      ]
    }
  }
}
```

Replace only the queue when integrating another service. Validate and generate:

```bash
./mvnw gonthera-cli:validate
./mvnw gonthera-cli:generate-sources
```

Do not edit the generated subscriber in `_gen`.

## Consumer Rabbit configuration

Create the concrete exchange configuration outside `_gen`:

```java
package com.example.service.config.messaging;

import com.example.service_gen.messaging.RabbitConfig;
import com.example.service_gen.messaging.RabbitExchange;
import org.springframework.context.annotation.Configuration;

@Configuration
@RabbitExchange("smart.payment.events")
public class PaymentRabbitConfig extends RabbitConfig {

    @Override
    protected String resolveExchangeName() {
        return "smart.payment.events";
    }
}
```

Keep the annotation to document the exchange contract, but override
`resolveExchangeName()`. In Gonthera CLI 2.0.1, Spring enhances `@Configuration`
with a CGLIB subclass and the generated `@RabbitExchange` annotation is not
`@Inherited`; relying only on the annotation makes `RabbitConfig` fail during
startup.

Create the listener outside `_gen`, under a feature package such as
`messaging/payment`:

```java
package com.example.service.messaging.payment;

import com.example.service_gen.messaging.sub.PaymentConfirmedSub;
import org.springframework.stereotype.Component;

@Component
public class PaymentConfirmedListener extends PaymentConfirmedSub {

    @Override
    protected void onMessage(String message) {
        // Deserialize, validate service, persist idempotently and grant the benefit.
    }
}
```

Keep business work in a service. The listener must only deserialize, validate and
delegate.

## Confirmed-payment message

Expect a JSON object equivalent to:

```json
{
  "paymentId": "75de18d7-a827-4ae8-ae32-4ebc44580b89",
  "clientId": "94cbeb99-56a7-49ce-ad13-c82e71ae25f1",
  "service": "CHURCH_LITE",
  "gateway": "INFINITEPAY",
  "orderNsu": "client-uuid-CHURCH_LITE-payment-uuid",
  "transactionNsu": "infinitepay-transaction-uuid",
  "amount": 1000,
  "paidAmount": 1010,
  "installments": 1,
  "captureMethod": "pix",
  "receiptUrl": "https://...",
  "paidAt": "2026-07-23T13:30:00"
}
```

Interpretation:

- `paymentId`: stable event/payment idempotency key.
- `clientId`: local object that must receive the purchased benefit.
- `service`: intended consumer. Reject or quarantine a mismatched value.
- `transactionNsu`: provider transaction identifier.
- `amount`: requested price in cents.
- `paidAmount`: amount reported as paid by the provider.
- `captureMethod`: normally `pix` or `credit_card`.

## Mandatory consumer behavior

Process every message idempotently:

1. Deserialize and validate required fields.
2. Verify that `service` matches the current application.
3. Start a database transaction.
4. Insert `paymentId` or `transactionNsu` into a local processed-payment/inbox
   table protected by a unique constraint.
5. If the unique key already exists, acknowledge without applying the benefit
   again.
6. Locate the local customer or subscription using `clientId`.
7. Validate the expected local price when the consumer owns a price contract.
8. Grant the plan, capacity or resource.
9. Commit the local transaction.

Never grant a benefit from `redirect_url`, browser query parameters or an
unverified client request.

RabbitMQ provides at-least-once delivery semantics in the intended architecture.
Assume duplicates and out-of-order retries can occur.

## Generated subscriber caveat

The current generated Java subscriber catches exceptions raised by `onMessage`.
Therefore, do not assume that throwing from `onMessage` will cause a broker retry.
Persist the incoming message in a local inbox before performing slow work, record
processing failures explicitly, and operate a local retry mechanism when the
consumer requires guaranteed completion.

If changing this behavior centrally, update the Gonthera subscriber template and
all consumer documentation instead of editing generated `_gen` classes.

## Package organization

Follow the repository package rule:

```text
config/messaging/PaymentRabbitConfig.java
messaging/payment/PaymentConfirmedListener.java
services/payment/PaymentConfirmationService.java
repositories/payment/ProcessedPaymentRepository.java
```

Keep generated contracts in `_gen` and all business implementation outside
`_gen`.
