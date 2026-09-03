# payment-service

Handles **payments and refunds**. It supports a real **Stripe** gateway and a **mock** gateway for local development, and it uses the **transactional outbox** pattern so payment events are published exactly once.

| Property | Value |
|----------|-------|
| HTTP port | 8084 (also the Stripe webhook) |
| gRPC port | 9095 |
| Store | PostgreSQL `paymentdb` |
| Publishes | `events.payment.completed / failed / refund-completed` |

## gRPC API

`payment/payment_service.proto`: `CreateOrderPaymentIntent`, `ConfirmMockPayment`.

- **`CreateOrderPaymentIntent`** — creates one payment intent for an order that may cover several bookings; returns the client secret and publishable key for the browser to mount Stripe Elements.
- **`ConfirmMockPayment`** — mock-gateway only; simulates the outcome from a test card number so the full flow works with no Stripe account.

## Package layout

`config · constants · controller · dto · entity · gateway · grpc · messaging · repository · scheduler · service · util · validation`

- **`gateway`** — the pluggable `PaymentGateway` interface with `StripePaymentGateway` and `MockPaymentGateway` implementations. `payment.gateway.type` (`stripe` | `mock`) selects one.
- **`controller`** — `StripeWebhookController`, the REST endpoint Stripe calls back with payment outcomes (signature-verified against the webhook secret).
- **`entity`** — `PaymentEntity`, `OutboxEventEntity`.
- **`scheduler`** — `OutboxPollingPublisher`.

## Payment gateway modes

| Mode | When | Cards |
|------|------|-------|
| `mock` | Local dev, demos, CI — no Stripe account needed | See [Payment test cards](payment-test-cards) |
| `stripe` | Real Stripe test keys, real Elements + webhooks | Stripe's own test cards |

Configure via `payment.gateway.type` and the `STRIPE_*` env vars — [Installation → Payment gateway modes](INSTALLATION).

## Transactional outbox

The critical guarantee: a `payment.*` event is published **if and only if** the payment row committed.

1. On a payment outcome, the payment row **and** an `outbox_events` row are written in the **same DB transaction**.
2. **`OutboxPollingPublisher`** (a ShedLock-guarded `@Scheduled` poller) reads unpublished outbox rows and sends them to Kafka, marking them published.
3. A crash between commit and publish is recovered on the next poll — no lost or phantom events.

Downstream, [booking-service](booking-service) consumes `payment.completed` to confirm the booking and `payment.failed` to release it; refunds flow on `payment.refund-completed`.

## Related

- [booking-service](booking-service) · [Communication patterns → outbox](communication-patterns) · [Payment test cards](payment-test-cards)
