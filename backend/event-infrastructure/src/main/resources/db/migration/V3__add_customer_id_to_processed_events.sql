-- V3__add_customer_id_to_processed_events.sql
-- Add customer_id to processed_events for tenant isolation of idempotency keys

ALTER TABLE processed_events 
    ADD COLUMN IF NOT EXISTS customer_id VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_processed_events_customer ON processed_events (customer_id, event_id);

-- Backfill existing records with a default (for dev only)
-- In production, this would need manual review
UPDATE processed_events SET customer_id = 'unknown' WHERE customer_id IS NULL;

-- Make it NOT NULL after backfill (optional, for production)
-- ALTER TABLE processed_events ALTER COLUMN customer_id SET NOT NULL;