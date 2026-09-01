# Cart Flow

The cart is **per-user and server-owned**: it lives in booking-service (PostgreSQL) and is
reached through the GraphQL gateway. The Angular frontend keeps a local signal that mirrors the
server state — every mutation returns the full cart, which replaces the mirror.

- **Owner:** booking-service, table `cart_items`, keyed by the JWT user.
- **Upsert:** a line is unique on `(user_id, event_id, seat_category)` — re-adding the same line
  updates its quantity instead of duplicating it.
- **Auth:** the user is always taken from the JWT (`GrpcUserContext`), never from the request.
  Guests who tap *Add to cart* are redirected to login.
- **Snapshot vs live:** each line stores a price/title snapshot (so it can be rendered and checked
  out), and also exposes a live `event` field hydrated from event-service on demand.

## Add to cart

```mermaid
sequenceDiagram
    participant U as User (PLP / PDP)
    participant FE as Frontend (CartService)
    participant GW as GraphQL Gateway
    participant B as Booking Service
    participant DB as PostgreSQL (cart_items)

    U->>FE: Add to cart (cheapest cat ×1, or chosen cat/qty)
    Note over FE: guest? → redirect to /auth/login
    FE->>GW: mutation addToCart(input) + JWT
    GW->>B: gRPC AddToCart (user from JWT)
    B->>B: validate + build AddCartItemDto
    B->>DB: upsert on (user, event, seat_category)
    B-->>GW: CartResponse (full cart)
    GW-->>FE: Cart { items, totalPrice, currency }
    FE->>FE: replace local signal mirror
```

The same path backs `updateCartItem` (by cart-line id), `removeFromCart` (by cart-line id) and
`clearCart`. Each returns the whole updated cart.

## Live event hydration

The cart's stored fields are a snapshot. When a query asks for `CartItem.event`, the gateway
hydrates live details from event-service — lazily (only when requested) and null-safe (if the
event was deleted, the field is `null`, the rest of the cart still renders).

```mermaid
sequenceDiagram
    participant C as Client query
    participant GW as GraphQL Gateway
    participant E as Event Service

    C->>GW: cart { items { eventTitle event { title venue { city } } } }
    GW->>E: getEvent(eventId)  (only because `event` was requested)
    alt event exists
        E-->>GW: EventInfo
        GW-->>C: item.event = { ...live details }
    else event removed
        E-->>GW: NOT_FOUND
        GW-->>C: item.event = null
    end
```

## Checkout uses the cart

Checkout turns the server cart into bookings and one payment. The **stable cart-line id is used as
each booking's idempotency key**, so reloading checkout reuses the same bookings (no duplicate seat
holds). A fresh `orderId` keys the single order payment.

```mermaid
sequenceDiagram
    participant FE as Checkout
    participant GW as GraphQL Gateway
    participant B as Booking Service
    participant P as Payment Service

    loop each cart line
        FE->>GW: createBooking(idempotencyKey = cartLine.id)
        GW->>B: gRPC CreateBooking
        B-->>FE: Booking (PENDING)
    end
    FE->>GW: createOrderPaymentIntent(orderId, bookingIds)
    GW->>P: one payment intent for the order
    P-->>FE: clientSecret / provider
    FE->>P: confirm payment (Stripe or mock)
    FE->>GW: clearCart
    FE->>FE: navigate to confirmation
```

See also: [Lovelist Flow](lovelist-flow.md) · [Overview](overview.md).
