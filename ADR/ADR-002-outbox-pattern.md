# ADR-002: Outbox Pattern for Atomicity

## Status

Accepted

## Context

We need to ensure that database transactions and event publishing are atomic. If we publish events directly from the application, a failure after DB commit but before Kafka publish results in lost events.

## Decision

Implement the **Outbox Pattern**.

### Design
1. Application writes business data + event to outbox table in single DB transaction
2. Separate process polls outbox table and publishes to Kafka
3. Published events are marked as sent (or deleted)
4. Idempotency keys prevent duplicate publishing

### Implementation
- `outbox_events` table with: id, aggregate_id, event_type, payload, created_at, sent_at
- `OutboxEventPublisher` runs as scheduled task or CDC consumer
- Transactional outbox with `@Transactional` on service methods

## Consequences

### Positive
- Guarantees atomicity between DB state and events
- Resilient to Kafka failures (events persist in DB)
- Simple to implement and understand
- Works with any messaging system

### Negative
- Additional table and polling mechanism
- Slight delay between DB commit and event availability
- Requires cleanup of processed outbox entries

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|-----------------|
| Transactional messaging (Kafka transactions) | Tightly couples app to Kafka, complex error handling |
| Two-phase commit | Heavyweight, requires XA-capable resources |
| Best-effort publishing | Event loss on failure |
