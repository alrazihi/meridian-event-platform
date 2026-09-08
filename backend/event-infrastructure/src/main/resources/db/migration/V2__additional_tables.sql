-- V2__additional_tables.sql
-- Additional tables for outbox pattern, CQRS projections, and order lines

CREATE TABLE IF NOT EXISTS order_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id VARCHAR(36) NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    sku VARCHAR(50) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(10,2) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_order_lines_order_id ON order_lines (order_id);

CREATE TABLE IF NOT EXISTS order_projections (
    order_id VARCHAR(36) PRIMARY KEY,
    customer_id VARCHAR(100) NOT NULL,
    total NUMERIC(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE outbox_events 
    ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_error TEXT,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_outbox_events_unsent ON outbox_events (sent_at) WHERE sent_at IS NULL;