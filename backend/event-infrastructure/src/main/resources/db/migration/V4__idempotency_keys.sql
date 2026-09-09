-- V4__idempotency_keys.sql
-- Idempotency keys for REST API endpoints

CREATE TABLE IF NOT EXISTS idempotency_keys (
    key_hash VARCHAR(64) PRIMARY KEY,
    endpoint VARCHAR(200) NOT NULL,
    response_body JSONB,
    status_code INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_idempotency_keys_endpoint ON idempotency_keys (endpoint);
CREATE INDEX IF NOT EXISTS idx_idempotency_keys_expires ON idempotency_keys (expires_at);
