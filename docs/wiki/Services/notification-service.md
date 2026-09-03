# notification-service

Sends **transactional email** in response to domain events. It is a pure Kafka consumer — no gRPC API, no database of its own — turning booking, event, and payment events into templated emails delivered over SMTP (MailHog locally).

| Property | Value |
|----------|-------|
| HTTP port | 8086 |
| gRPC | none |
| Store | none |
| Consumes | `booking.*`, `event.*`, `payment.failed` (+ each topic's `-dlt`) |

## Package layout

`config · constants · email · grpc · health · messaging`

- **`messaging`** — `@KafkaListener` consumers for each topic, plus dedicated **DLT listeners** (`*-dlt`) that capture messages which failed all retries.
- **`email`** — the sender and template binding.
- **`health`** — a readiness check for the mail transport.

## Email templates

FreeMarker/HTML templates under `resources/templates/`:

| Template | Trigger |
|----------|---------|
| `booking-confirmation.html` | `events.booking.confirmed` — includes event name and details |
| `booking-cancellation.html` | `events.booking.cancelled` |
| `event-created.html` | `events.event.created` |
| `event-published.html` | `events.event.published` |
| `event-updated.html` | `events.event.updated` |
| `event-cancelled.html` | `events.event.cancelled` |
| `event-reminder.html` | scheduled reminder |

## Delivery and failure handling

- Each consumer has a matching **dead-letter** listener; a message that keeps failing lands on `<topic>-dlt` and is logged for inspection rather than blocking the partition or being silently dropped.
- Recipient addresses are resolved from [user-service](user-service) where needed.
- Locally, all mail is captured by **MailHog** — open `http://localhost:8025` to read it (no real mail is sent). Keycloak's own verification emails also land here.

> Email **verification** is handled by Keycloak directly (see [user-service](user-service)), not by this service.

## Related

- [Communication patterns → Kafka & DLT](communication-patterns) · [Observability](observability)
