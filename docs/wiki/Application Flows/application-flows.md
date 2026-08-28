# Application Flows

These diagrams describe the Booking Platform's architecture and its main runtime flows. The request-flow sequences and event lifecycle were moved out of the README to keep the front page concise (the README keeps the top-level architecture diagram).

## Architecture

```mermaid
flowchart TB
    Client(["Client — Angular SPA"])
    GW["GraphQL Gateway :8080<br/>JWT · rate limit · routing"]
    Client -->|GraphQL| GW

    GW -->|gRPC| USER["User :8081"]
    GW -->|gRPC| EVENT["Event :8082"]
    GW -->|gRPC| BOOK["Booking :8083"]
    GW -->|gRPC| PAY["Payment :8084"]
    GW -->|gRPC| TICK["Ticket :8088"]
    GW -->|gRPC| ANAL["Analytics :8087"]

    USER --> UDB[("PostgreSQL<br/>userdb")]
    EVENT --> EDB[("MongoDB<br/>eventdb")]
    BOOK --> BDB[("PostgreSQL<br/>bookingdb")]
    PAY --> PDB[("PostgreSQL<br/>paymentdb")]
    TICK --> TDB[("MongoDB<br/>ticketdb")]
    ANAL --> ADB[("MongoDB<br/>analyticsdb")]
    BOOK --> REDIS[("Redis<br/>locks · rate limit")]
    GW -.->|rate limit| REDIS

    subgraph SS["Semantic Search — Spring AI"]
        OLLAMA["Ollama<br/>nomic-embed-text"]
        VEC[("MongoDB<br/>event_vectors<br/>$vectorSearch")]
    end
    EVENT -->|embed| OLLAMA
    EVENT -->|upsert / search| VEC

    KAFKA{{"Apache Kafka<br/>booking.* · event.* · payment.*"}}
    BOOK --> KAFKA
    EVENT --> KAFKA
    PAY --> KAFKA
    KAFKA --> NOTIF["Notification :8086<br/>Email via SMTP"]
    KAFKA -.->|reindex events| EVENT

    CONFIG["Config Server :8888"]
    EUREKA["Eureka :8761"]
```

## Request Flow: Creating a Booking

```mermaid
sequenceDiagram
    participant C as Client
    participant GW as GraphQL Gateway
    participant B as Booking Service
    participant R as Redis
    participant E as Event Service
    participant K as Kafka
    participant P as Payment Service
    participant T as Ticket Service
    participant N as Notification Service
    participant A as Analytics Service

    C->>GW: createBooking (JWT + rate limit)
    GW->>B: gRPC
    B->>R: acquire seat lock
    B->>E: gRPC — check availability
    B->>B: persist booking (PENDING)
    B->>K: publish BookingCreated
    K->>P: process payment
    P->>K: publish PaymentCompleted
    K->>B: update status → CONFIRMED
    B->>K: publish BookingConfirmed
    par fan-out on BookingConfirmed
        K->>T: generate tickets
        K->>N: send confirmation email
        K->>A: record metrics
    end
    B->>R: release lock
```

## Request Flow: Cancelling a Booking (with refund)

Cancellation is the reverse saga. The booking is marked cancelled and seats are restored synchronously; the refund, ticket cancellation, and notification happen asynchronously off `BookingCancelled`. A paid booking follows `CANCELLED → REFUND_PENDING → REFUNDED`.

```mermaid
sequenceDiagram
    participant C as Client
    participant GW as GraphQL Gateway
    participant B as Booking Service
    participant E as Event Service
    participant K as Kafka
    participant P as Payment Service
    participant T as Ticket Service
    participant N as Notification Service

    C->>GW: cancelBooking
    GW->>B: gRPC
    B->>B: status → CANCELLED<br/>(REFUND_PENDING if already paid)
    B->>E: gRPC — restore seats (+quantity)
    B->>K: publish BookingCancelled
    B-->>GW: Booking (CANCELLED)
    GW-->>C: Booking

    Note over K: fan-out (async)
    par refund (only if paid)
        K->>P: process refund
        P->>K: publish PaymentRefundCompleted
        K->>B: status → REFUNDED
    and cancel tickets
        K->>T: mark tickets CANCELLED
    and notify
        K->>N: cancellation email
    end
```

> **Payment failure (unhappy path):** if the payment **fails** during booking, payment-service emits `PaymentFailed`; booking-service marks the booking failed (releasing the reserved seats) and notification-service emails the user. The booking is never confirmed and no ticket is generated.

## Request Flow: Creating an Event

An organizer (`employee` role) creates an event. The write path is synchronous and simple — validate, persist as `DRAFT`, publish one event — while the side effects (organizer email, semantic indexing) happen asynchronously off the Kafka event, so the API responds immediately.

```mermaid
sequenceDiagram
    participant C as Organizer (employee)
    participant GW as GraphQL Gateway
    participant E as Event Service
    participant K as Kafka
    participant N as Notification Service
    participant U as User Service
    participant O as Ollama

    C->>GW: createEvent (JWT — employee role)
    GW->>E: gRPC CreateEvent
    E->>E: validate (dates, seats, venue)
    E->>E: persist → MongoDB eventdb (status DRAFT)
    E->>K: publish EVENT_CREATED
    E-->>GW: Event (DRAFT)
    GW-->>C: Event

    Note over K: fan-out (async, off the write path)
    par notify organizer
        K->>N: onEventCreated
        N->>U: gRPC getUserEmail(organizerId)
        N->>N: send "event draft created" email
    and semantic index (if SEMANTIC_SEARCH_ENABLED)
        K->>E: VectorIndexConsumer
        E->>O: embed event text
        E->>E: upsert → event_vectors
    end
```

## Event Lifecycle

An event moves through a small state machine. Only `DRAFT` events can be **published**; only `DRAFT` or `PUBLISHED` events can be **cancelled**. Every transition emits a Kafka event consumed by notification-service (organizer email) and analytics-service, and drives the semantic index in event-service (create/publish → index, cancel → remove).

```mermaid
stateDiagram-v2
    [*] --> DRAFT: createEvent
    DRAFT --> PUBLISHED: publishEvent
    DRAFT --> CANCELLED: cancelEvent
    PUBLISHED --> CANCELLED: cancelEvent
    CANCELLED --> [*]
    COMPLETED --> [*]
    note right of PUBLISHED
        Visible to public + semantic search
    end note
    note right of COMPLETED
        Terminal — past events (seed history);
        no automatic runtime transition yet
    end note
```

- **`DRAFT → PUBLISHED`** (`publishEvent` → `EVENT_PUBLISHED`) — makes the event visible to public and semantic search.
- **`DRAFT → CANCELLED`** — cancels a draft that was never public (removes its vector if it was indexed).
- **`PUBLISHED → CANCELLED`** — pulls a live event from search (`EVENT_CANCELLED` → removed from the vector store, organizer notified). Note: this does **not** auto-cancel existing bookings.
- **`COMPLETED`** — terminal status for past events; currently only produced by seed data (no scheduled job transitions live events to it yet).
