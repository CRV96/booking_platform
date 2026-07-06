-- =============================================================================
-- V2: Transactional Outbox table (P4-04)
--
-- Stores domain events that must be published to Kafka reliably.
-- Events are written in the SAME transaction as the payment state change,
-- then a scheduled poller reads and publishes them asynchronously.
-- =============================================================================

CREATE TABLE outbox_events (
    id              UUID        DEFAULT gen_random_uuid() PRIMARY KEY,
    aggregate_type  VARCHAR(100) NOT NULL,     -- e.g. "Payment"
    aggregate_id    VARCHAR(100) NOT NULL,     -- e.g. payment UUID
    event_type      VARCHAR(100) NOT NULL,     -- e.g. "PaymentCompleted", "PaymentFailed"
    payload         JSONB        NOT NULL,     -- full event data as JSON
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMP                  -- NULL = not yet published
);

-- The poller queries: WHERE published_at IS NULL ORDER BY created_at
-- Partial index keeps this fast (only indexes unpublished rows)
CREATE INDEX idx_outbox_unpublished
    ON outbox_events (created_at)
    WHERE published_at IS NULL;

-- Cleanup deletes: WHERE published_at < NOW() - INTERVAL '24 hours'
-- Partial index covers only published rows
CREATE INDEX idx_outbox_published_cleanup
    ON outbox_events (published_at)
    WHERE published_at IS NOT NULL;
