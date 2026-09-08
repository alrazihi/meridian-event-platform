# ADR-003: Kafka for Event Streaming

## Status

Accepted

## Context

We need a message broker that supports:
- High throughput for event streaming
- Partitioning and ordering guarantees
- Retention and replay capabilities
- Ecosystem for stream processing

## Decision

Use **Apache Kafka** as the event streaming platform.

### Topic Design
- `order.events` — order lifecycle events (partitioned by order_id)
- `payment.events` — payment events (partitioned by payment_id)
- `inventory.events` — inventory events (partitioned by sku)
- `dlq.order.events` — dead-letter queue for failed order events
- `dlq.payment.events` — dead-letter queue for failed payment events

### Ordering Guarantees
- Ordering guaranteed per partition (per aggregate ID)
- Keys used for partitioning: order_id, payment_id, sku
- Exactly-once processing with idempotent consumers

## Consequences

### Positive
- High throughput and low latency
- Durable log with configurable retention
- Replay capability for recovery and testing
- Rich ecosystem (Kafka Streams, Connect, ksqlDB)

### Negative
- Operational complexity
- Requires careful topic/partition planning
- Exactly-once semantics require careful implementation

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|-----------------|
| RabbitMQ | Lower throughput, no built-in replay |
| AWS SQS | No ordering guarantees, vendor lock-in |
| Database polling | Tight coupling, poor latency |
