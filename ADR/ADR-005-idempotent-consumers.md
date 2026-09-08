# ADR-005: Idempotent Consumers with Offset Management

## Status

Accepted

## Context

Consumers must handle duplicate events, retries, and failures without data corruption or duplicate processing.

## Decision

Implement **idempotent consumers** with explicit offset management.

### Strategy
- Track processed event IDs in database
- Check idempotency key before processing
- Commit offsets only after successful processing
- Use Kafka's `enable.auto.commit=false` with manual ack

### Duplicate Handling
- Deduplication table: `processed_events (event_id, processed_at)`
- TTL-based cleanup of old entries
- Idempotency key: `aggregate_id + event_type + sequence`

## Consequences

### Positive
- Safe retries without side effects
- Exactly-once processing semantics
- Resilience to consumer failures
- Clear audit trail of processed events

### Negative
- Additional database writes per event
- Cleanup mechanism required for processed_events table
- Slightly increased processing latency

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|-----------------|
| Auto-commit offsets | Risk of data loss on failure |
| Exactly-once processing (EOS) | Complex, requires idempotent producers anyway |
| No deduplication | Risk of duplicate processing |
