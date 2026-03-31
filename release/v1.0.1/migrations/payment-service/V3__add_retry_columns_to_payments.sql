-- ============================================================================
-- V3: Add retry columns to payments table
-- ============================================================================
-- Supports the PENDING_RETRY → PROCESSING → COMPLETED/FAILED state transitions
-- by tracking how many retries have been attempted, the configured maximum,
-- and when the next retry should be attempted (exponential backoff).
--
-- retry_count    — incremented each time the scheduler starts a retry attempt.
-- max_retries    — copied from config at payment creation time; allows per-payment
--                  override if needed in the future.
-- next_retry_at  — scheduler only picks up payments where next_retry_at <= NOW().
--                  NULL when the payment is not in PENDING_RETRY.
-- ============================================================================

ALTER TABLE payments
    ADD COLUMN retry_count   INT         NOT NULL DEFAULT 0,
    ADD COLUMN max_retries   INT         NOT NULL DEFAULT 3,
    ADD COLUMN next_retry_at TIMESTAMPTZ NULL;

-- Partial index: only PENDING_RETRY rows need this lookup — avoids scanning
-- completed/failed records and keeps the index small.
CREATE INDEX idx_payments_pending_retry
    ON payments (next_retry_at)
    WHERE status = 'PENDING_RETRY';
