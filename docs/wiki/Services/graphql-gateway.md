# graphql-gateway

The **single API entry point** for the Angular SPA. It hosts one GraphQL schema, authenticates every request, enforces rate limits, and translates each GraphQL field into gRPC calls to the domain services. It owns no business data of its own.

| Property | Value |
|----------|-------|
| HTTP port | 8080 (`/graphql`, `/graphiql`) |
| gRPC | client only — to every domain service |
| Store | Redis (rate-limit counters) |
| Depends on | config, eureka, and the services it proxies |

In Docker the gateway has **no published port** — it is reachable only through [nginx](infrastructure-overview) on port 80.

## Package layout

`annotations · config · constants · controller · dto · exception · filter · graphql · grpc · properties · security · service`

- **`graphql/resolver`** — one resolver per domain: `AuthResolver`, `UserResolver`, `EventResolver`, `BookingResolver`, `CartResolver`, `LovelistResolver`, `PaymentResolver`, `TicketResolver`. Resolvers use `@QueryMapping` / `@MutationMapping`, and `@SchemaMapping` for lazy relationships (e.g. a booking's live `event`).
- **`grpc/client`** — a typed client per service (interface + `impl`) wrapping the generated stubs.
- **`security`** — JWT validation, the `@PublicEndpoint` annotation + `AuthenticationAspect` that gate which operations may run unauthenticated, and role checks (`authService.requireRole(...)`).
- **`filter`** — request filters including client-IP handling and rate limiting.
- **`dto` / schema** — GraphQL types in `resources/graphql/*.graphqls`, mapped from protobuf responses.

## The schema

Assembled from per-domain files: `user-`, `event-`, `booking-`, `cart-`, `lovelist-`, `payment-`, `ticket-schema.graphqls`. Highlights:

- **Queries:** `me`, `user`, `users`, `event`, `events` (with `aiSearch`), `booking`, `myBookings`, `eventBookings` (organizer stats), `cart`, `lovelist`, `myTickets`, `ticket`, `ticketsByBooking`, `ticketsByUser`.
- **Mutations:** `register` / `login` / `logout` / `refreshToken`, `updateProfile`, `createEvent` / `updateEvent` / `publishEvent` / `cancelEvent`, `createBooking` / `cancelBooking` / `discardBooking`, cart and lovelist mutations, `createOrderPaymentIntent` / `confirmMockPayment`, `validateTicket` / `cancelTicket`.

A full example query set is in the [README](https://github.com/CRV96/booking_platform/blob/main/README.md) and the [Postman collection](INSTALLATION).

## Responsibilities in detail

- **Authentication** — validates the JWT once per request and forwards the user context to services over gRPC metadata; downstream services trust it and do not re-validate.
- **Authorization** — resolvers check the caller's role for privileged operations (organizer-only event management and ticket validation; admin-only user search; owner-only reads like `eventBookings`).
- **Rate limiting** — Redis-backed sliding windows per tier (anonymous / authenticated / search). See [Security](https://github.com/CRV96/booking_platform/blob/main/SECURITY.md).
- **Error mapping** — gRPC status codes from services become structured GraphQL errors with an [error code](error-codes).

## Related

- [Communication patterns](communication-patterns) · [Frontend guide](frontend-guide) · [Application flows](application-flows)
