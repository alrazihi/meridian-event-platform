# Deployment Guide

## Prerequisites

- Docker Engine 24+
- Docker Compose v2+
- 8GB RAM minimum
- 4 CPU cores minimum

## Local Development

```bash
cd C:\gitproject\meridian-event-platform
docker compose up --build
```

## Services

| Service | URL | Credentials |
|---------|-----|-------------|
| Backend API | http://localhost:8080/api/v1 | JWT token |
| Kafka | localhost:9092 | - |
| Kafka Connect | http://localhost:8083 | - |
| Kafka UI | http://localhost:9000 | - |
| PostgreSQL | localhost:5432 | meridian/meridian |

## Register Debezium Connector

```bash
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d @deploy/kafka-connect/postgres-connector.json
```

## Verify CDC Pipeline

```bash
# Check connector status
curl http://localhost:8083/connectors/meridian-postgres-connector/status

# Consume order events
docker exec meridian-kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic order.events \
  --from-beginning
```

## Production Considerations

- Use managed Kafka (Confluent, MSK)
- Use managed PostgreSQL (RDS, Cloud SQL)
- Enable Kafka ACLs and TLS
- Use secrets management (Vault, AWS Secrets Manager)
- Configure proper retention policies
- Set up monitoring and alerting
