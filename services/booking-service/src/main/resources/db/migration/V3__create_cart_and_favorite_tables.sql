-- ============================================================================
-- V3: Create cart_items and favorites tables
-- ============================================================================
-- Per-user Cart and Lovelist (favorites), owned by booking-service since a cart
-- line is essentially a proto-booking and this service is already user-scoped.
--
-- Design notes:
--   - UUID primary keys via gen_random_uuid() — consistent with bookings.
--   - cart_items carries a price/title snapshot so the cart can be shown and
--     checked out without a round-trip; the authoritative amount is still
--     re-derived from event-service at checkout.
--   - UNIQUE(user_id, event_id, seat_category) makes "add to cart" an upsert:
--     the same line can't be duplicated, only its quantity updated.
--   - favorites is a pure pointer (user_id, event_id); display fields are
--     hydrated live from event-service, so nothing here goes stale.
--   - version column powers Hibernate optimistic locking on cart quantity edits.
-- ============================================================================

CREATE TABLE cart_items (
    id            UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       VARCHAR(255)    NOT NULL,
    event_id      VARCHAR(255)    NOT NULL,
    event_title   VARCHAR(500)    NOT NULL,
    seat_category VARCHAR(255)    NOT NULL,
    quantity      INTEGER         NOT NULL CHECK (quantity > 0),
    unit_price    DECIMAL(10, 2)  NOT NULL,
    currency      VARCHAR(3)      NOT NULL DEFAULT 'USD',
    created_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    version       BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT uq_cart_user_event_category UNIQUE (user_id, event_id, seat_category)
);

-- Primary access pattern: load a user's whole cart.
CREATE INDEX idx_cart_items_user_id ON cart_items (user_id);

CREATE TABLE favorites (
    id         UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    VARCHAR(255)  NOT NULL,
    event_id   VARCHAR(255)  NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_favorite_user_event UNIQUE (user_id, event_id)
);

-- Primary access pattern: load a user's whole lovelist.
CREATE INDEX idx_favorites_user_id ON favorites (user_id);
