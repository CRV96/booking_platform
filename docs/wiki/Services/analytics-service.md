# analytics-service

Builds **real-time booking and revenue analytics** by consuming domain events into aggregate documents, and exposes them over a small REST API. This is the one domain service the platform reads via REST rather than gRPC (the metrics are read-heavy, dashboard-style data).

| Property | Value |
|----------|-------|
| HTTP port | 8087 |
| gRPC port | 9097 (reserved) |
| Store | MongoDB `analyticsdb` |
| Consumes | `events.booking.confirmed / cancelled`, `events.payment.completed` |

## Package layout

`config · constants · controller · document · dto · messaging · repository · service`

- **`document`** — aggregate models: `EventStats`, `DailyMetrics`, `CategoryStats`, `EventLog`.
- **`messaging`** — Kafka consumers that fold each event into the aggregates.
- **`controller`** — `AnalyticsController`, the REST surface.

## REST API — `/api/analytics`

| Endpoint | Returns |
|----------|---------|
| `.../events` | Stats for all events |
| `.../events/{id}` | Detailed stats for one event |
| `.../events/{id}/lifecycle` | Event lifecycle timeline |
| `.../revenue/top` | Top events by revenue |
| `.../revenue/by-category` | Revenue split by category |
| `.../bookings/trends` | Booking volume over time |
| `.../payments/trends` | Payment volume over time |
| `.../bookings/cancellation-rate` | Cancellation rate |
| `.../bookings/average-value` | Average booking value |

(Exact paths are defined by the `Api` constants in the controller.)

## How it stays current

Every confirmed booking, cancellation, and completed payment is a Kafka event; the consumers update the aggregate documents as they arrive, so the analytics reflect near-real-time state without querying the operational services.

> **Note:** the organizer *statistics page* in the frontend is served by the gateway's `eventBookings` query against [booking-service](booking-service), not by this REST API. analytics-service provides the broader platform-wide metrics and is not currently fronted by the gateway.

## Related

- [booking-service](booking-service) · [payment-service](payment-service) · [Observability](observability)
