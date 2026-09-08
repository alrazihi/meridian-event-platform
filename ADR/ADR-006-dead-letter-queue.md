# ADR-006: Dead-Letter Queue with Retry

## Status

Accepted

## Context

Some events will fail processing due to transient errors (network, downstream unavailable) or poison messages (corrupt data). We need a strategy that preserves failed events for later analysis without blocking the main processing pipeline.

## Decision

Implement **Dead-Letter Queue (DLQ)** with exponential backoff retry.

### Retry Strategy
- Max attempts: 3
- Backoff: exponential (1s, 2s, 4s)
- Retryable exceptions: `TransientDataAccessException`, `HttpClientErrorException`
- Non-retryable: `IllegalArgumentException`, `JsonProcessingException`

### DLQ Design
- Topics: `dlq.order.events`, `dlq.payment.events`, `dlq.inventory.events`
- Headers: `X-Original-Topic`, `X-Retry-Count`, `X-First-Failure-Timestamp`, `X-Exception-Class`
- Consumer group: `dlq-processor` for manual intervention

### Recovery
- DLQ messages can be replayed via admin API
- Dead-letter events stored in database for audit
- Alerting on DLQ depth threshold

## Consequences

### Positive
- Failed events preserved for analysis
- Main pipeline remains unblocked
- Configurable retry logic
- Clear separation between retryable and poison messages

### Negative
- Additional Kafka topics and consumers
- Complexity in retry logic
- DLQ can grow if not monitored

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|-----------------|
| Immediate failure | Blocks pipeline, requires manual intervention |
| Infinite retry | Can cause thundering herd |
| Skip and log | Risk of data loss |
