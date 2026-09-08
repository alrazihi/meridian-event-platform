# ADR-001: Debezium for Change Data Capture

## Status

Accepted

## Context

We need to capture database changes reliably and propagate them to downstream systems. Options include:
- Application-level event publishing
- Database triggers
- CDC with Debezium

## Decision

Use **Debezium** for Change Data Capture.

### Rationale
- Captures changes at the database level, ensuring no data is missed
- Guarantees exactly-once delivery semantics when combined with Kafka
- Captures before/after images for audit trails
- Supports schema evolution and DDL events
- Decouples application logic from event publishing
- Resilient to application failures (changes captured even if app is down)

### Tradeoffs
- Additional infrastructure component (Kafka Connect)
- Requires WAL level logical replication in PostgreSQL
- Initial snapshot can be resource-intensive on large tables

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|-----------------|
| Application events | Tight coupling, missed events on failure |
| Database triggers | Hard to maintain, no schema evolution |
| Polling | Inefficient, eventual consistency |
