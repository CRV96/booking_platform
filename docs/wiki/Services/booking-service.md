# booking-service

The heart of the platform. Reserves seats **safely under concurrency** using Redis distributed locks and idempotency keys, manages the PENDING → CONFIRMED/CANCELLED lifecycle, and also backs the shopping **cart** and **lovelist** (favorites).

| Property | Value |
|----------|-------|
| HTTP port | 8083 |
| gRPC port | 9094 |
| Store | PostgreSQL `bookingdb` + Redis (locks) |
| Publishes | `events.booking.created / confirmed / cancelled` |
| Consumes | `events.payment.completed / failed / refund-completed` |

## gRPC APIs

Three proto services:

- **Booking** (`booking_service.proto`): `CreateBooking`, `GetBooking`, `GetUserBookings`, `CancelBooking`, `DiscardBooking`, `GetBookingAttendees`, `GetEventBookings`.
- **Cart** (`cart.proto`): `GetCart`, `AddToCart`, `UpdateCartItem`, `RemoveFromCart`, `ClearCart`.
- **Lovelist** (`lovelist.proto`): `AddToLoveList`, `RemoveFromLoveList`, `GetLoveList`.

`GetEventBookings` powers the organizer statistics page (owner-gated at the gateway); `GetBookingAttendees` lets other services resolve who is attending an event.

## Package layout

`constants · dto · entity · exception · grpc · lock · mapper · messaging · properties · repository · scheduler · service · util · validation`

- **`entity`** — `BookingEntity`, `CartItemEntity`, `FavoriteEntity`.
- **`lock`** — the Redis distributed-lock abstraction used around seat updates.
- **`scheduler`** — `BookingExpirationScheduler`.

## Concurrency safety

Two mechanisms together prevent double-booking and duplicate submissions:

1. **Distributed locking** — seat reservation for an event/category runs inside a Redis lock, so concurrent requests are serialized and cannot oversell.
2. **Idempotency keys** — `CreateBooking` takes a client-generated `idempotencyKey`; a retry with the same key returns the existing booking instead of creating a second one. The checkout flow uses the stable cart-line id as the key, so reloading checkout reuses the same bookings.

On reservation, booking-service calls [event-service](event-service) `UpdateSeatAvailability` to decrement seats; on cancel/expiry it restores them.

## Booking lifecycle

- **`CreateBooking`** → `PENDING`, seats held, `events.booking.created` published, a hold-expiry timer set.
- **Payment** → on `events.payment.completed` the booking becomes `CONFIRMED` and `events.booking.confirmed` is published (ticket-service issues tickets, notification emails, analytics updates). On `events.payment.failed` it is released.
- **`CancelBooking`** → `CANCELLED`, seats released, `events.booking.cancelled` published (triggers ticket cancellation and a refund path).
- **`DiscardBooking`** → hard-deletes an unpaid PENDING booking and releases its seats with **no** event/email — used when a customer abandons checkout, so nothing lingers in "My Bookings".

## Background jobs

- **`BookingExpirationScheduler`** — releases seats for PENDING bookings whose hold expired (covers customers who leave without paying and never trigger a discard). ShedLock-guarded.

## Related

- [payment-service](payment-service) · [ticket-service](ticket-service) · [Cart & Lovelist overview](overview) · [Application flows](application-flows)
