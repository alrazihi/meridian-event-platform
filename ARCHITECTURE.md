# Architecture Documentation

## System Context

```
┌─────────────┐     REST      ┌──────────────────────────┐
│   Client    │◄─────────────►│  event-platform-service   │
│             │               │  (Spring Boot)            │
└─────────────┘               └─────────────┬────────────┘
                                            │
                      ┌─────────────────────┼─────────────────────┐
                      │                     │                     │
                 ┌────▼────┐          ┌────▼────┐          ┌────▼────┐
                 │PostgreSQL│          │  Kafka  │          │  Kafka  │
                 │ (source) │          │(broker) │          │ Connect │
                 └────┬────┘          └────┬────┘          └────┬────┘
                      │                     │                     │
                      │           ┌─────────┼─────────┐           │
                      │           │         │         │           │
                      │        ┌──▼──┐   ┌──▼──┐   ┌──▼──┐        │
                      │        │Consumer│ │Consumer│ │Consumer│     │
                      │        │  A   │   │  B   │   │  C   │     │
                      │        └──────┘   └──────┘   └──────┘     │
                      │           │         │         │           │
                      │        ┌──▼──┐   ┌──▼──┐   ┌──▼──┐        │
                      │        │Proj. │ │Audit │ │Notif│         │
                      │        │      │ │      │ │      │         │
                      │        └──────┘   └──────┘   └──────┘     │
                      │                                             │
                 ┌────▼────────────────────────────────────────────▼────┐
                 │              Event Processing Pipeline               │
                 │  Ingestion → Validation → Routing → Projection → Sink │
                 └───────────────────────────────────────────────────────┘
```

## Container Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    event-platform-service                     │
│                                                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ REST API    │  │  Admin API  │  │  Kafka Consumers     │  │
│  │ Controllers │  │  (internal) │  │  (projection, audit) │  │
│  └──────┬──────┘  └──────┬──────┘  └──────────┬──────────┘  │
│         │                │                     │             │
│  ┌──────▼────────────────▼─────────────────────▼──────────┐  │
│  │              Application Services                       │  │
│  └──────┬────────────────────┬───────────────────┬────────┘  │
│         │                    │                   │           │
│  ┌──────▼──────┐    ┌────────▼────────┐  ┌──────▼────────┐  │
│  │  Domain     │    │  Persistence     │  │  Messaging     │  │
│  │  Services   │    │  (JPA/Flyway)    │  │  (Kafka)       │  │
│  │  Entities   │    │  + Outbox        │  │  + DLQ         │  │
│  └─────────────┘    └─────────────────┘  └───────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

## Component Architecture

### Domain Layer
- `Order` aggregate root
- `Payment` aggregate root
- `InventoryItem` aggregate root
- `DomainEvent` base class
- Value objects: `Money`, `OrderId`, `PaymentId`, `Sku`
- Domain services: `OrderValidator`, `PaymentProcessor`, `InventoryReserver`

### Application Layer
- **Inbound Ports:**
  - `PlaceOrderUseCase`
  - `ProcessPaymentUseCase`
  - `ReserveInventoryUseCase`
  - `QueryOrderStatusUseCase`
- **Outbound Ports:**
  - `OrderRepository`
  - `EventPublisher`
  - `NotificationService`

### Infrastructure Layer
- **Persistence:** JPA entities, Flyway migrations, Spring Data repositories, Outbox pattern
- **Messaging:** Kafka producer/consumer, Debezium integration, DLQ handling
- **Security:** JWT authentication, method security

### Runtime Components
- **Debezium:** Configured for CDC but the application uses its own outbox pattern for event publishing
- **Kafka Connect:** Manages Debezium connector
- **Kafka Broker:** Message transport
- **Schema Registry:** Available in docker-compose but not actively used by the application (events use JSON serialization)
- **Kafka Streams:** Not implemented; read model is maintained by a synchronous consumer

## Key Design Decisions

See [ADR directory](ADR/) for detailed rationale.

| ADR | Decision |
|-----|----------|
| ADR-001 | Debezium for CDC over application-level events |
| ADR-002 | Outbox pattern for atomicity |
| ADR-003 | Kafka for event streaming |
| ADR-004 | Schema evolution with Avro |
| ADR-005 | Idempotent consumers with offset tracking |
| ADR-006 | DLQ with retry and backoff |

## Transaction Flow

```
┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
│  Client  │────►│   App    │────►│  Outbox  │────►│  Kafka   │
│          │     │ Service  │     │  Table   │     │ Producer │
└──────────┘     └──────────┘     └──────────┘     └──────────┘
     │                                                      │
     │                                                      ▼
     │                                            ┌──────────────────┐
     │                                            │  Kafka Topic      │
     │                                            │  (order.events)   │
     │                                            └────────┬─────────┘
     │                                                     │
     │                                                     ▼
     │                                            ┌──────────────────┐
     │                                            │  Consumer Group   │
     │                                            │  (projection)     │
     │                                            └────────┬─────────┘
     │                                                     │
     │                                                     ▼
     │                                            ┌──────────────────┐
     │                                            │  Read Model       │
     │                                            │  (materialized)   │
     │                                            └──────────────────┘
```

## Failure Handling Strategy

### Producer Failures
- Network timeout: retry with exponential backoff (3 attempts)
- Kafka unavailable: persist to outbox, retry later
- Serialization failure: send to DLQ with payload for inspection

### Consumer Failures
- Processing exception: retry with backoff, then DLQ
- Deserialization failure: DLQ immediately (poison message)
- Downstream timeout: circuit breaker, fallback to async retry

### Debezium Failures
- Connector restart: resumes from last committed offset
- Schema change: DDL event emitted, consumers handle gracefully
- Database unavailable: Kafka Connect retries, alerts on prolonged outage
