# Lovelist (Favorites) Flow

The lovelist is a **per-user list of favorited events**, owned by booking-service (PostgreSQL) and
reached through the GraphQL gateway. Unlike the cart, a favorite is a **pure pointer** —
`(user_id, event_id)` — with no denormalized event fields; everything shown on screen is hydrated
live from event-service, so a favorite never goes stale.

- **Owner:** booking-service, table `favorites`, unique on `(user_id, event_id)`.
- **Idempotent:** adding an event that's already favorited is a no-op that returns the existing row
  (and is safe under concurrent double-clicks — the unique constraint is caught and the existing
  row is returned).
- **Auth:** user comes from the JWT. Guests who tap ♥ are redirected to login.
- **Response shape:** every mutation returns the **full updated lovelist**, so the frontend mirror
  is always fresh in one round trip.

## Toggle a favorite

```mermaid
sequenceDiagram
    participant U as User (PLP / PDP ♥)
    participant FE as Frontend (LovelistService)
    participant GW as GraphQL Gateway
    participant B as Booking Service
    participant DB as PostgreSQL (favorites)

    U->>FE: tap ♥ on an event
    Note over FE: guest? → redirect to /auth/login
    alt not yet loved
        FE->>GW: mutation addFavorite(eventId) + JWT
        GW->>B: gRPC AddToLoveList (user from JWT)
        B->>DB: insert (user, event) — idempotent on unique key
    else already loved
        FE->>GW: mutation removeFavorite(eventId) + JWT
        GW->>B: gRPC RemoveFromLoveList
        B->>DB: delete (user, event) — no-op if absent
    end
    B-->>GW: LoveListResponse (full lovelist)
    GW-->>FE: [LovelistItem]
    FE->>FE: replace local signal mirror (heart state + nav count update)
```

## Lovelist page (hydrated)

The Lovelist page lists loved events with their live details. Each entry's `event` field is
hydrated from event-service on demand; if an event was removed, the row shows a graceful
"no longer available" fallback instead of failing the query.

```mermaid
sequenceDiagram
    participant C as Lovelist page
    participant GW as GraphQL Gateway
    participant B as Booking Service
    participant E as Event Service

    C->>GW: query lovelist { eventId event { title category venue { city } dateTime } }
    GW->>B: gRPC GetLoveList (user from JWT)
    B-->>GW: [ { eventId, createdAt } ]
    loop each entry (only because `event` was requested)
        GW->>E: getEvent(eventId)
        alt exists
            E-->>GW: EventInfo → event = { ...live details }
        else removed
            E-->>GW: NOT_FOUND → event = null
        end
    end
    GW-->>C: hydrated lovelist
```

See also: [Cart flow](cart-flow) · [Overview](overview).
