# Observability Guide for Operators

This document describes what operators can observe, important failure signals, health endpoints, relevant metrics, and how to troubleshoot common failures in the Meridian Event Platform.

---

## What Operators Can Observe

### 1. Structured Logs (JSON Format)

All logs are emitted as JSON with consistent fields:
```json
{
  "@timestamp": "2024-01-15T10:30:45.123Z",
  "level": "INFO",
  "logger": "WORKFLOW",
  "message": "Payment approved",
  "traceId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "spanId": "1234567890abcdef",
  "correlationId": "order-123-corr-456",
  "paymentId": "pay-789",
  "transactionId": "txn_abc123",
  "orderId": "order-123",
  "service": "meridian-event-platform",
  "environment": "production"
}
```

**Log Categories:**
| Logger | Purpose | File |
|--------|---------|------|
| `WORKFLOW` | Business workflow events (orders, payments, inventory) | `workflow.log` |
| `EVENT_PROCESSING` | Kafka consumer/producer events | `event-processing.log` |
| `SECURITY_AUDIT` | Security-relevant events (auth, authz, rate limits) | `security-audit.log` |
| Root/other | Framework/infrastructure | Console |

**Correlation IDs:**
- `X-Correlation-ID` - Business transaction ID (propagated across services)
- `X-Trace-ID` - Distributed trace ID
- `X-Span-ID` - Current span ID
- All automatically injected into Kafka headers and log MDC

### 2. Health & Readiness Endpoints

| Endpoint | Purpose | Auth |
|----------|---------|------|
| `GET /actuator/health/liveness` | Kubernetes liveness probe | None |
| `GET /actuator/health/readiness` | Kubernetes readiness probe | None |
| `GET /actuator/health` | Full health with details | When authorized |
| `GET /actuator/info` | Build/version info | None |
| `GET /actuator/metrics` | All metrics | When authorized |
| `GET /actuator/prometheus` | Prometheus scrape format | None |

**Health Indicators:**
| Indicator | Checks | Degraded When |
|-----------|--------|---------------|
| `db` | PostgreSQL connectivity + migrations | Connection fails |
| `kafka` | Broker metadata fetch | Broker unreachable |
| `outbox` | Pending events count, age, retries | >1000 pending or >5min old |
| `eventProcessing` | Recent processed events, Kafka connectivity | No events processed recently or Kafka down |
| `dbPool` | HikariCP usage, waiting threads | >85% usage or threads waiting |

### 3. Key Metrics (Prometheus)

#### Business Metrics
| Metric | Type | Description | Alert Threshold |
|--------|------|-------------|-----------------|
| `meridian.orders.placed` | Counter | Orders placed | - |
| `meridian.orders.confirmed` | Counter | Orders confirmed via events | - |
| `meridian.payments.initiated` | Counter | Payments created (PENDING) | - |
| `meridian.payments.approved` | Counter | Payments approved | - |
| `meridian.payments.rejected` | Counter | Payments rejected | >5% of initiated |
| `meridian.inventory.reservations` | Counter | Successful reservations | - |
| `meridian.inventory.reservation_failures` | Counter | Failed reservations | >10% of attempts |
| `meridian.outbox.pending` | Gauge | Pending outbox events | >1000 |
| `meridian.payments.pending` | Gauge | Payments in PENDING state | >100 |

#### Event Processing Metrics
| Metric | Type | Description | Alert Threshold |
|--------|------|-------------|-----------------|
| `meridian.events.published` | Counter | Events written to outbox | - |
| `meridian.events.processed` | Counter | Events successfully processed | - |
| `meridian.events.failed` | Counter | Events failed after max retries | >0 |
| `meridian.events.dlq` | Counter | Events sent to DLQ | >0 |
| `meridian.outbox.published` | Counter | Outbox events sent to Kafka | - |
| `meridian.outbox.failed` | Counter | Outbox publish failures (retryable) | - |
| `meridian.outbox.dlq` | Counter | Outbox events sent to DLQ | >0 |

#### Infrastructure Metrics (Spring Boot + Micrometer)
| Metric | Description |
|--------|-------------|
| `http.server.requests` | Request latency, count, errors by URI/status |
| `hikaricp.connections.active` | Active DB connections |
| `hikaricp.connections.idle` | Idle DB connections |
| `hikaricp.connections.pending` | Threads waiting for connection |
| `kafka.consumer.records.consumed` | Records consumed per topic |
| `kafka.producer.record.send-rate` | Records sent per second |
| `jvm.memory.used` | Heap/non-heap memory usage |
| `jvm.gc.pause` | GC pause duration |

---

## Important Failure Signals

### Immediate Alerting (Page)
| Signal | Query | Meaning |
|--------|-------|---------|
| Health DOWN | `health_status{status="DOWN"} == 1` | Service unhealthy |
| DB pool exhausted | `hikaricp.connections.pending > 0` | Connection starvation |
| Outbox backlog | `meridian.outbox.pending > 1000` | Events not publishing |
| Payment rejections spike | `rate(meridian.payments.rejected[5m]) > 0.1` | Gateway or fraud issue |
| DLQ growth | `increase(meridian.events.dlq[5m]) > 0` | Poison pills or systemic failure |
| No event processing | `rate(meridian.events.processed[5m]) == 0 AND meridan.outbox.pending > 0` | Consumer down |

### Warning Alerts (Ticket)
| Signal | Query | Meaning |
|--------|-------|---------|
| High connection usage | `hikaricp.connections.active / hikaricp.connections.max > 0.85` | Pool pressure |
| Old pending events | `meridian.outbox.old_pending > 0` | Events stuck >5min |
| Outbox retries | `meridian.outbox.retrying > 10` | Transient Kafka issues |
| Low inventory | `meridian.inventory.low_stock > 0` | Stock replenishment needed |
| Slow HTTP requests | `histogram_quantile(0.95, rate(http.server.requests.duration[5m])) > 2s` | Performance degradation |

---

## Health Endpoints Detail

### `/actuator/health/liveness`
```json
{
  "status": "UP"
}
```
Returns UP if process is alive. Used for Kubernetes liveness probe.

### `/actuator/health/readiness`
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "kafka": {"status": "UP"},
    "outbox": {"status": "UP", "details": {"pendingEvents": 5, "oldPendingEvents": 0}},
    "eventProcessing": {"status": "UP", "details": {"kafkaConnectivity": "UP"}},
    "dbPool": {"status": "UP", "details": {"usagePercent": 0.35, "waitingThreads": 0}}
  }
}
```
Returns UP only if all critical components are healthy. Used for Kubernetes readiness probe.

### `/actuator/health` (with authorization)
Full details including all component metrics.

---

## Relevant Metrics for Dashboards

### Business Dashboard
- **Order throughput**: `rate(meridian.orders.placed[5m])` vs `rate(meridian.orders.confirmed[5m])`
- **Payment success rate**: `rate(meridian.payments.approved[5m]) / rate(meridian.payments.initiated[5m])`
- **Inventory utilization**: `meridian.inventory.reservations` over time
- **Pending payments**: `meridian.payments.pending` (should be near 0)

### Event Processing Dashboard
- **Outbox lag**: `meridian.outbox.pending` (target: <100)
- **Event processing rate**: `rate(meridian.events.processed[5m])` by event type
- **Failure rate**: `rate(meridian.events.failed[5m])` / `rate(meridian.events.published[5m])`
- **DLQ rate**: `rate(meridian.events.dlq[5m])` + `rate(meridian.outbox.dlq[5m])`
- **Consumer lag**: Kafka consumer group lag (via Kafka metrics)

### Infrastructure Dashboard
- **HTTP latency**: p50, p95, p99 of `http.server.requests`
- **Error rate**: `rate(http.server.requests{status=~"5.."}[5m])`
- **DB pool**: active/idle/pending connections over time
- **JVM**: heap usage, GC frequency/pause
- **Kafka**: produce/consume rates, request latency

---

## Troubleshooting Common Failures

### 1. Outbox Events Not Publishing
**Symptoms**: `meridian.outbox.pending` growing, `meridian.outbox.old_pending > 0`
**Checks**:
1. Check Kafka connectivity: `health` endpoint → `kafka` component
2. Check outbox publisher logs: `grep "Outbox event" event-processing.log`
3. Check for repeated failures: `meridian.outbox.failed` counter
4. Verify broker SASL auth in logs

**Resolution**:
- If Kafka down: Wait for broker recovery (auto-retries)
- If auth failure: Verify `KAFKA_CLIENT_PASSWORD` env var
- If DLQ growing: Check `dlq.outbox.events` topic for error patterns

### 2. Events Not Being Processed
**Symptoms**: `meridian.outbox.pending` low but `meridian.events.processed` not increasing
**Checks**:
1. Consumer health: `health` endpoint → `eventProcessing` component
2. Consumer logs: `grep "Received event" event-processing.log`
3. Check consumer group lag: `kafka-consumer-groups.sh --group order-projection --describe`
4. Verify `processed_events` table not blocking (unique constraint)

**Resolution**:
- If consumer down: Restart pod (K8s will restart)
- If offset stuck: Check for poison pill in DLQ, then reset offset
- If duplicate detection blocking: Verify idempotency key logic

### 3. Payment Stuck in PENDING
**Symptoms**: `meridian.payments.pending` > 0 for extended period
**Checks**:
1. Check async completion logs: `grep "Payment completion" workflow.log`
2. Check payment gateway errors: `grep "Payment gateway failed" workflow.log`
3. Verify `PaymentGateway` implementation health

**Resolution**:
- If gateway timeout: Check gateway latency/availability
- If gateway error: Check error message in logs, fix root cause
- Manual recovery: Call `POST /api/v1/payments/{id}/complete` (if endpoint exists) or re-trigger async completion

### 4. Inventory Overselling / Reservation Failures
**Symptoms**: `meridian.inventory.reservation_failures` spike
**Checks**:
1. Check stock levels: `SELECT sku, available_quantity FROM inventory_items WHERE available_quantity < 10`
2. Check reservation logs: `grep "Insufficient stock" workflow.log`
3. Verify no race conditions: `meridian.inventory.reservation_failures` should be low under normal load

**Resolution**:
- If genuine stockout: Replenish inventory
- If race condition: Verify `@Version` optimistic locking on `InventoryItemEntity`
- If bug: Check reservation logic in `DefaultInventoryService`

### 5. High Error Rates / Slow Responses
**Symptoms**: HTTP 5xx spike, p99 latency > 5s
**Checks**:
1. Check application logs: `grep "ERROR" workflow.log event-processing.log`
2. Check DB: Slow queries, lock contention (`pg_stat_activity`)
3. Check Kafka: Producer/consumer latency metrics
4. Check thread pools: Tomcat threads, Kafka consumer threads

**Resolution**:
- DB: Add missing indexes, check long-running transactions
- Kafka: Increase partitions, check consumer concurrency
- App: Scale horizontally (add pods)

### 6. Security Audit Anomalies
**Signals**: `SECURITY_AUDIT` logs showing:
- `AUTH_FAILURE` spike → Brute force or credential stuffing
- `AUTHZ_DENIED` spike → Possible privilege escalation attempt
- `RATE_LIMIT_EXCEEDED` → DoS or misconfigured client

**Response**: Check audit logs, block IPs at WAF/load balancer, rotate compromised credentials.

---

## Security/Audit Events

All security events logged to `SECURITY_AUDIT` logger (file: `security-audit.log`):

| Event Type | Fields | When |
|------------|--------|------|
| `AUTH_SUCCESS` | customerId, ip, endpoint | Successful JWT validation |
| `AUTH_FAILURE` | customerId, ip, endpoint, reason | Invalid/expired token |
| `AUTHZ_DENIED` | customerId, ip, endpoint, resource, reason | Role or ownership check failed |
| `ORDER_ACCESS` | customerId, ip, orderId, action | Order view/place |
| `PAYMENT_ATTEMPT` | customerId, ip, orderId, amount, result | Payment init/approve/reject |
| `INVENTORY_RESERVATION` | customerId, ip, sku, quantity, result | Reserve/release |
| `ADMIN_ACTION` | adminId, ip, action, resource, details | Admin operations |
| `RATE_LIMIT_EXCEEDED` | ip, endpoint | Rate limit hit |

**PII Protection**: All IDs (customerId, orderId, etc.) are HMAC-SHA256 pseudonymized (12-char prefix). Raw values never logged.

---

## Log Retention

| Log File | Retention | Size Cap |
|----------|-----------|----------|
| `workflow.log` | 30 days | 500 MB |
| `event-processing.log` | 30 days | 500 MB |
| `security-audit.log` | 90 days | 1 GB |

Configured in `logback-spring.xml`. Adjust `maxHistory` and `totalSizeCap` as needed.

---

## Quick Reference Commands

```bash
# Check service health
curl -s http://localhost:8080/actuator/health/readiness | jq .

# View Prometheus metrics
curl -s http://localhost:8080/actuator/prometheus | grep meridian

# Search logs for correlation ID
grep "corr-abc123" workflow.log event-processing.log security-audit.log

# Check outbox status
kubectl exec -it postgres -- psql -U meridian -c "SELECT count(*) FROM outbox_events WHERE sent_at IS NULL;"

# Check consumer lag
kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group order-projection --describe

# View DLQ messages
kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic dlq.order.events --from-beginning --max-messages 5
```

---

## Adding Custom Metrics

To add a new business metric:
1. Inject `BusinessMetrics` bean
2. Call appropriate counter/gauge method
3. Metric automatically appears in `/actuator/prometheus`

Example:
```java
@Service
public class MyService {
    private final BusinessMetrics metrics;
    
    public MyService(BusinessMetrics metrics) { this.metrics = metrics; }
    
    public void doWork() {
        metrics.incrementCustomCounter(); // Add method to BusinessMetrics
    }
}
```

---

## Verification Checklist for New Deployments

- [ ] `/actuator/health/readiness` returns UP
- [ ] `/actuator/prometheus` returns metrics including `meridian.*`
- [ ] Logs appear in JSON format with `traceId`, `correlationId`
- [ ] Security audit events appear in `security-audit.log`
- [ ] Outbox publisher processing events (check `event-processing.log`)
- [ ] Consumer processing events (check `event-processing.log` for "Event processed")
- [ ] Payment saga completes (PENDING → APPROVED/REJECTED in logs)
- [ ] No ERROR level logs in startup sequence