# meridian-event-platform

**Event-Driven Architecture Reference Implementation**

![Java](https://img.shields.io/badge/Java-21-blue)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3-green)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)
![Debezium](https://img.shields.io/badge/Debezium-2.5-red)
![Kafka](https://img.shields.io/badge/Kafka-3.7-black)
![Docker](https://img.shields.io/badge/Docker_Compose-blue)

> **Important:** This is an original reference implementation demonstrating enterprise event-driven architecture and CDC patterns. It is **not affiliated with or derived from** proprietary IBM, Debezium, or customer implementations.

## Purpose

Meridian Event Platform demonstrates a production-grade event-driven architecture using Change Data Capture (CDC) with Debezium, Apache Kafka, and Spring Boot. It shows how to build reliable, scalable, and maintainable event-driven systems with proper transaction boundaries, idempotency, and failure handling.

**Workflow demonstrated:**
1. Application writes transaction to PostgreSQL
2. Debezium captures change events from WAL
3. Events flow through Kafka topics
4. Downstream consumers process events
5. Read model projections are updated
6. Notifications and audit logs are emitted

## Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Java 21, Spring Boot 3.3 |
| Database | PostgreSQL 16 |
| CDC | Debezium 2.5 |
| Messaging | Apache Kafka 3.7, Kafka Connect |
| Streaming | Spring for Kafka |
| Testing | JUnit 5, Testcontainers |
| Build | Maven |
| Containerization | Docker Compose |
| CI/CD | GitHub Actions |

## Quick Start

```bash
git clone https://github.com/alrazihi/meridian-event-platform.git
cd meridian-event-platform
docker compose up --build
```

- Backend API: http://localhost:8080/api/v1
- Kafka: localhost:9092
- Kafka Connect: http://localhost:8083
- PostgreSQL: localhost:5432

## Documentation

- [Architecture](ARCHITECTURE.md)
- [ADRs](ADR/)
- [Deployment](docs/deployment/docker-compose.md)
- [Observability](docs/observability/observability.md)
- [Security](docs/security/security.md)
- [Testing](docs/testing/test-strategy.md)

## Architecture Highlights

- **CDC with Debezium** for reliable change data capture
- **Exactly-once semantics** with Kafka transactions
- **Event schema evolution** with backward compatibility
- **Idempotent consumers** with offset management
- **Dead-letter queues** for poison messages
- **Retry with backoff** for transient failures
- **CQRS** with materialized views
- **Outbox pattern** for atomicity
- **Observability** with Micrometer + OpenTelemetry

## Project Structure

```
meridian-event-platform/
├── backend/
│   ├── event-domain/
│   ├── event-application/
│   ├── event-infrastructure/
│   └── event-service/
├── deploy/
│   └── kafka-connect/
├── docker-compose.yml
├── .github/workflows/ci.yml
└── docs/
```

## License

MIT
