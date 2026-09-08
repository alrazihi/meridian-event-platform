# Observability

## Metrics

### Business Metrics
- `orders.placed` — counter for orders placed
- `orders.confirmed` — counter for orders confirmed
- `payments.processed` — counter for payments processed
- `payments.failed` — counter for payment failures
- `inventory.reserved` — counter for inventory reservations
- `events.published` — counter for events published
- `events.consumed` — counter for events consumed
- `events.dlq` — counter for events sent to DLQ

### System Metrics
- `kafka.producer.record.send.total` — Kafka producer send rate
- `kafka.consumer.record.consume.total` — Kafka consumer consume rate
- `kafka.consumer.lag` — consumer lag per partition
- `jvm.memory.used` — JVM memory usage
- `jvm.gc.pause` — GC pause times
- `db.connection.pool.active` — HikariCP active connections
- `db.query.duration` — database query durations

## Tracing

### Distributed Tracing with OpenTelemetry
- Trace ID propagated via Kafka message headers
- Span attributes: `orderId`, `paymentId`, `sku`, `eventType`
- Export to Jaeger or Tempo

### Key Spans
1. `placeOrder` — from API request to DB commit
2. `publishEvent` — from event creation to Kafka ack
3. `consumeEvent` — from Kafka poll to processing complete
4. `db.transaction` — database transaction duration

## Logging

### Structured Logging
- JSON format via Logback encoder
- Correlation ID in MDC for request tracing
- Log levels:
  - ERROR: exceptions, DLQ events
  - WARN: retries, circuit breaker opens
  - INFO: business events (order placed, payment processed)
  - DEBUG: event payloads, Kafka offsets

### Log Fields
- `timestamp`
- `serviceName`
- `traceId`
- `spanId`
- `orderId`
- `eventType`
- `kafka.topic`
- `kafka.partition`
- `kafka.offset`

## Dashboards

### Grafana Dashboards
1. **Order Processing** — orders placed/min, confirmation rate, processing time
2. **Event Pipeline** — events published/consumed, consumer lag, DLQ depth
3. **System Health** — JVM memory, GC, DB connections, Kafka broker metrics
4. **Business Metrics** — revenue, order volume by status, inventory levels

## Alerting

| Alert | Condition | Severity |
|-------|-----------|----------|
| Consumer lag > 1000 | `kafka.consumer.lag > 1000` | Critical |
| DLQ depth > 100 | `events.dlq > 100` | High |
| Payment failure rate > 10% | `payments.failed / payments.processed > 0.1` | High |
| DB connection pool > 80% | `db.connection.pool.active / max > 0.8` | Medium |
| Kafka producer errors > 5/min | `kafka.producer.error > 5` | Medium |
