# Meridian Event Platform — Operations Runbook

## Table of Contents
1. [Service Overview](#service-overview)
2. [Deployment](#deployment)
3. [Health Checks](#health-checks)
4. [Common Failure Scenarios](#common-failure-scenarios)
5. [Troubleshooting](#troubleshooting)
6. [Scaling](#scaling)
7. [Backup and Restore](#backup-and-restore)

## Service Overview

| Component | Port | Purpose |
|-----------|------|---------|
| HTTP API | 8080 | REST endpoints for orders and payments |
| Actuator | 8080 | Health, metrics, prometheus |
| Prometheus | 8080 | `/actuator/prometheus` |
| Kafka | 9092 | Event streaming |
| PostgreSQL | 5432 | Primary data store |

## Deployment

### Prerequisites
- Kubernetes 1.28+
- PostgreSQL 16+ with RLS enabled
- Kafka 3.7+ with SASL/SSL
- Prometheus + Alertmanager

### Deploy to Kubernetes
```bash
kubectl apply -f deploy/kubernetes/namespace.yaml
kubectl apply -f deploy/kubernetes/rbac.yaml
kubectl apply -f deploy/kubernetes/configmap.yaml
kubectl apply -f deploy/kubernetes/secret.yaml
kubectl apply -f deploy/kubernetes/deployment.yaml
kubectl apply -f deploy/kubernetes/service.yaml
kubectl apply -f deploy/kubernetes/ingress.yaml
```

### Verify Deployment
```bash
kubectl rollout status deployment/meridian-event-platform -n meridian
kubectl get pods -n meridian
```

## Health Checks

| Endpoint | Purpose | Auto-restart |
|----------|---------|--------------|
| `/actuator/health/liveness` | Container is running | Yes |
| `/actuator/health/readiness` | Ready to serve traffic | No (used by service mesh) |
| `/actuator/health` | Full health with details | No |

### Custom Health Indicators
- `db` — PostgreSQL connectivity
- `kafka` — Kafka broker connectivity
- `outbox` — Outbox publisher status
- `eventProcessing` — Consumer lag status

## Common Failure Scenarios

### 1. Database Unavailable
**Symptoms:** `PoolTimeoutException` in logs, health check fails
**Diagnosis:**
```bash
kubectl logs -l app=meridian-event-platform -n meridian --tail=100 | grep -i "database\|connection"
```
**Remediation:**
- Check PostgreSQL pod: `kubectl get pods -l app=postgres -n meridian`
- Check connection limits: `SELECT count(*) FROM pg_stat_activity;`
- Application will retry automatically (HikariCP backoff)

### 2. Kafka Unavailable
**Symptoms:** `OutboxEventPublisher` logs show retry attempts, DLQ depth growing
**Diagnosis:**
```bash
kubectl logs -l app=meridian-event-platform -n meridian --tail=100 | grep -i "kafka\|timeout"
```
**Remediation:**
- Check Kafka pod status
- Verify topic configuration: `kafka-topics.sh --bootstrap-server meridian-kafka:9092 --describe`
- Outbox pattern ensures no data loss; events publish when Kafka recovers

### 3. High Consumer Lag
**Symptoms:** `order_projections` stale, `KafkaConsumerLag` alert firing
**Diagnosis:**
```bash
curl http://meridian-event-platform:8080/actuator/prometheus | grep kafka_consumer_group_lag
```
**Remediation:**
- Check consumer thread health in logs
- Increase consumer `concurrency` (requires partition increase)
- Check for poison messages in DLQ

### 4. Outbox Events Stuck
**Symptoms:** `outbox_events` table growing, `OutboxEventsAccumulating` alert
**Diagnosis:**
```bash
psql -h meridian-postgres -U meridian -c "SELECT count(*) FROM outbox_events WHERE sent_at IS NULL;"
```
**Remediation:**
- Check Kafka connectivity
- Verify `OutboxEventPublisher` is scheduled (check logs for "Publishing outbox events")
- Manual restart of pod triggers immediate publish

### 5. Optimistic Locking Failures
**Symptoms:** `OptimisticLockingFailureException` in logs
**Diagnosis:** Concurrent updates to same aggregate
**Remediation:**
- This is expected behavior — retry with backoff
- If persistent, investigate hot rows: `SELECT id, version FROM orders WHERE version > 100;`

## Troubleshooting

### Quick Diagnostic Commands
```bash
# Check pod status
kubectl get pods -n meridian -l app=meridian-event-platform

# Check recent logs
kubectl logs -l app=meridian-event-platform -n meridian --tail=200

# Check metrics
kubectl port-forward service/meridian-event-platform 8080:8080 -n meridian
curl http://localhost:8080/actuator/prometheus | head -50

# Check database
kubectl exec -it meridian-postgres-0 -n meridian -- psql -U meridian -d meridian -c "SELECT count(*) FROM orders;"

# Check Kafka topics
kubectl exec -it meridian-kafka-0 -n meridian -- kafka-topics.sh --bootstrap-server localhost:9092 --list
```

### Log Locations
- Application logs: `/var/log/meridian/application.log`
- Security audit logs: `/var/log/meridian/security-audit.log`
- Container logs: `kubectl logs -l app=meridian-event-platform -n meridian`

### Key Metrics to Monitor
| Metric | Source | Alert Threshold |
|--------|--------|-----------------|
| `http_server_requests_seconds_count` | Micrometer | P95 > 1s |
| `jvm_memory_used_bytes` | Micrometer | Heap > 85% |
| `kafka_consumer_group_lag` | Micrometer | > 100 messages |
| `hikaricp_connections_pending` | Micrometer | > 5 |
| `outbox_events_total` | Custom | PENDING rate > 10/s |
| `events_processed_total` | Custom | Sudden drop |

## Scaling

### Horizontal Scaling
```bash
kubectl scale deployment/meridian-event-platform --replicas=5 -n meridian
```

**Considerations:**
- Kafka consumer concurrency is 1 (single partition). To scale consumers, increase partitions:
  ```bash
  kafka-topics.sh --bootstrap-server meridian-kafka:9092 --alter --topic order.events --partitions 3
  ```
- Outbox publisher uses `SELECT ... FOR UPDATE SKIP LOCKED` to prevent duplicate sends across instances
- Rate limiter is in-memory (not shared across instances) — consider Redis for distributed rate limiting

### Vertical Scaling
- Memory: 512Mi–2Gi recommended
- CPU: 250m–1000m recommended
- JVM heap: 75% of container memory limit

## Backup and Restore

### PostgreSQL Backup
```bash
# Full backup
kubectl exec -it meridian-postgres-0 -n meridian -- pg_dump -U meridian meridian > backup.sql

# Schema only
kubectl exec -it meridian-postgres-0 -n meridian -- pg_dump -U meridian --schema-only meridian > schema.sql
```

### Kafka Backup
- Topic data is replicated (replication factor = 3 in production)
- For critical topics, consider MirrorMaker 2 for cross-cluster replication

### Recovery Procedure
1. Restore PostgreSQL from backup
2. Verify Flyway schema version: `SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;`
3. Restart application pods: `kubectl rollout restart deployment/meridian-event-platform -n meridian`
4. Verify health: `kubectl get pods -n meridian`
5. Verify outbox publisher is processing: check logs for "Publishing outbox events"
