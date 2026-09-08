# ADR-004: Schema Evolution with Avro

## Status

Accepted

## Context

Event schemas will evolve over time. We need a strategy that allows producers and consumers to evolve independently without breaking compatibility.

## Decision

Use **Apache Avro** for event serialization with schema registry.

### Compatibility Rules
- Producers: backward compatible (new fields optional)
- Consumers: forward compatible (ignore unknown fields)
- Schema registry enforces compatibility checks

### Evolution Strategy
- Additive changes only (new optional fields)
- No removal or renaming of fields
- Deprecate fields before removal (grace period)

## Consequences

### Positive
- Strong typing and compact binary format
- Schema registry provides compatibility checks
- Enables schema evolution without breaking consumers
- Better performance than JSON/XML

### Negative
- Requires schema registry infrastructure
- Additional tooling for schema management
- Less human-readable than JSON

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|-----------------|
| JSON | No schema enforcement, larger payloads |
| Protobuf | Good alternative, but Avro more common in Kafka ecosystem |
| Thrift | Less common in Kafka ecosystem |
