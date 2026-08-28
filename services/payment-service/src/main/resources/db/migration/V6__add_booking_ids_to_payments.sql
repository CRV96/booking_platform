-- A payment can cover multiple bookings (an "order"). booking_ids holds the comma-separated
-- list of booking IDs the payment pays for. NULL for legacy single-booking payments, which
-- fall back to the existing booking_id column.
ALTER TABLE payments ADD COLUMN booking_ids TEXT;
