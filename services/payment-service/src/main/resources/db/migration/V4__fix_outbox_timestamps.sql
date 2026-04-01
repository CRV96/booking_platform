-- ============================================================================
-- V4: Fix outbox_events timestamp columns to use TIMESTAMPTZ
-- ============================================================================
-- V2 created created_at and published_at as TIMESTAMP (no timezone), while the
-- payments table and the rest of the schema use TIMESTAMPTZ. The entity maps
-- both columns to java.time.Instant, which is timezone-aware. Storing Instant
-- in a TIMESTAMP column causes PostgreSQL to interpret the value in the session
-- timezone, which breaks cross-timezone ordering and comparisons.
--
-- This migration converts both columns to TIMESTAMPTZ, interpreting existing
-- values as UTC (the correct assumption for any Instant stored without a zone).
-- ============================================================================

ALTER TABLE outbox_events
    ALTER COLUMN created_at   TYPE TIMESTAMPTZ USING created_at   AT TIME ZONE 'UTC',
    ALTER COLUMN published_at TYPE TIMESTAMPTZ USING published_at AT TIME ZONE 'UTC';
