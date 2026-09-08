# Failure Scenario Documentation

This document catalogs all identified failure scenarios for the Meridian Event Platform, their current behavior, safety assessment, and implemented fixes with regression tests.

---

## Architecture Overview for Failure Analysis

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│   REST API  │────▶│  Application │────▶│  Database   │
│  (Spring)   │     │   Services   │     │ (PostgreSQL)│
└─────────────┘     └──────┬───────┘     └─────────────┘
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
         ┌─────────┐  ┌──────────┐ ┌──────────┐
         │ Outbox  │  │  Kafka   │ │ Payment  │
         │ (DB)    │  │  Broker  │ │ Gateway  │
         └────┬────┘  └────┬─────┘ └──────────┘
              │            │
              ▼            ▼
         ┌──────────┐ ┌──────────┐
         │ Publisher│ │ Consumer │
         │ (Scheduled)│ (Kafka)  │
         └──────────┘ └──────────┘
```

---

## Failure Scenarios

### 1. Database Unavailable

| Aspect | Detail |
|--------|--------|
| **Trigger** | PostgreSQL container down, network partition, connection pool exhausted |
| **Current Behavior** | HikariCP throws `PoolTimeoutException` after 30s; `@Transactional` rolls back; client gets 500 |
| **Safety** | ✅ Safe - no partial writes due to transaction boundaries |
| **Fix** | Connection pool config: `maximum-pool-size: 20`, `connection-timeout: 30000` |
| **Test** | `DatabaseUnavailableTest.shouldRollbackOrderWhenEventPublishFails` |
| **Expected** | Fast fail (30s max), no data corruption, client can retry |

---

### 2. Transaction Rollback

| Aspect | Detail |
|--------|--------|
| **Trigger** | Constraint violation, optimistic lock failure, explicit `throw` in `@Transactional` |
| **Current Behavior** | Spring rolls back entire transaction; outbox write rolled back; no event published |
| **Safety** | ✅ Safe - atomicity guaranteed |
| **Fix** | Transactional outbox pattern ensures event write is in same TX as business data |
| **Test** | `DatabaseUnavailableTest.shouldNotCreateOrderWhenValidationFails` |
| **Expected** | All-or-nothing: order + outbox event either both persist or both rollback |

---

### 3. Kafka Unavailable (Producer)

| Aspect | Detail |
|--------|--------|
| **Trigger** | Broker down, network partition, SASL auth failure |
| **Current Behavior** | `KafkaEventPublisher.publish()` writes to outbox (DB) immediately; `OutboxEventPublisher` retries on schedule |
| **Safety** | ✅ Safe - events never lost, persisted to outbox first |
| **Fix** | Producer config: `acks=all`, `retries=3`, `enable.idempotence=true`; outbox publisher with pessimistic locking |
| **Test** | `KafkaUnavailableTest.shouldPersistToOutboxWhenKafkaUnavailable`, `shouldRetryAndEventuallySendWhenKafkaRecovers` |
| **Expected** | Events queued in outbox, delivered when Kafka recovers, exactly-once |

---

### 4. Kafka Unavailable (Consumer)

| Aspect | Detail |
|--------|--------|
| **Trigger** | Broker down during consumption, consumer can't fetch |
| **Current Behavior** | `@KafkaListener` pauses; no offset committed; on recovery, re-reads from last committed offset |
| **Safety** | ✅ Safe - `enable.auto.commit=false`, offset committed only after successful processing |
| **Fix** | Manual acknowledgment after DB commit |
| **Test** | `ConsumerRestartTest.shouldResumeFromCommittedOffsetAfterRestart` |
| **Expected** | No message loss, no duplicate processing (idempotency handles redelivery) |

---

### 5. Duplicate Event (Redelivery)

| Aspect | Detail |
|--------|--------|
| **Trigger** | Kafka redelivery (crash before ack), producer retry, mirror maker duplication |
| **Current Behavior** | `OrderEventConsumer` checks `processed_events` table (unique on `event_id + customer_id`) before processing |
| **Safety** | ✅ Safe - exactly-once semantics via idempotency key |
| **Fix** | Composite unique constraint `(event_id, customer_id)` on `processed_events` table |
| **Test** | `DuplicateEventTest.shouldProcessEventExactlyOnceDespiteRedelivery`, `shouldHandleConcurrentDuplicateDeliveries` |
| **Expected** | Single processing regardless of delivery count; tenant isolation preserved |

---

### 6. Consumer Restart

| Aspect | Detail |
|--------|--------|
| **Trigger** | Pod restart, deployment, crash, OOM kill |
| **Current Behavior** | New consumer starts from last committed offset; idempotency prevents reprocessing |
| **Safety** | ✅ Safe - offset committed AFTER DB write + processed_event insert |
| **Fix** | Transactional consumer: `transactionTemplate.execute()` wraps projection + processed_event; `acknowledgment.acknowledge()` after |
| **Test** | `ConsumerRestartTest.shouldResumeFromCommittedOffsetAfterRestart`, `shouldNotReprocessIfAcknowledgedButNotPersisted` |
| **Expected** | Resume from last committed offset; no duplicates; no gaps |

---

### 7. Message Processing Failure (Transient)

| Aspect | Detail |
|--------|--------|
| **Trigger** | Temporary DB deadlock, network blip, projection handler exception |
| **Current Behavior** | `@Retryable(maxAttempts=3, backoff=exponential)` retries; if succeeds, commits offset |
| **Safety** | ✅ Safe - transient failures don't cause data loss |
| **Fix** | Spring Retry with exponential backoff (1s, 2s, 4s) |
| **Test** | `MessageProcessingFailureTest.shouldRetryOnTransientFailureAndEventuallySucceed` |
| **Expected** | Up to 3 retries with backoff; success commits offset |

---

### 8. Message Processing Failure (Permanent)

| Aspect | Detail |
|--------|--------|
| **Trigger** | Malformed event, unknown event type, poison pill |
| **Current Behavior** | Retries exhausted (3 attempts) → `@Recover` sends to DLQ → acknowledges original |
| **Safety** | ✅ Safe - poison pills don't block partition; DLQ preserves for debugging |
| **Fix** | DLQ topic per event type; `@Recover` method with original payload + error |
| **Test** | `MessageProcessingFailureTest.shouldSendToDLQAfterMaxRetriesExhausted`, `shouldNotRetryIndefinitely`, `shouldNotBlockPartitionOnPoisonPill` |
| **Expected** | Max 4 attempts (1 initial + 3 retries); DLQ receives failure context; partition continues |

---

### 9. Retry Exhaustion (Outbox Publisher)

| Aspect | Detail |
|--------|--------|
| **Trigger** | Kafka unavailable for extended period, persistent send failures |
| **Current Behavior** | `OutboxEventPublisher` retries up to 5 times; on exhaustion, sends to `dlq.outbox.events` and marks `sent_at` |
| **Safety** | ✅ Safe - failed events don't block batch; DLQ preserves payload |
| **Fix** | `MAX_RETRIES = 5`; pessimistic locking prevents duplicate send attempts |
| **Test** | `RetryExhaustionTest.shouldMoveToDLQAfterMaxRetries`, `shouldNotRetryEventsAlreadyAtMaxRetries` |
| **Expected** | After 5 failures: DLQ event with original payload + error + retry count; outbox marked done |

---

### 10. Malformed Event

| Aspect | Detail |
|--------|--------|
| **Trigger** | Invalid JSON, missing fields, wrong schema, null payload |
| **Current Behavior** | Jackson deserialization fails → retry → DLQ |
| **Safety** | ✅ Safe - consumer doesn't crash; partition continues |
| **Fix** | Graceful deserialization error handling; DLQ capture |
| **Test** | `MalformedEventTest.shouldHandleInvalidJSONGracefully`, `shouldHandleMissingRequiredFields`, `shouldHandleNullPayload` |
| **Expected** | Invalid events → DLQ; valid events after still processed |

---

### 11. Stale State (Optimistic Locking)

| Aspect | Detail |
|--------|--------|
| **Trigger** | Concurrent updates to same aggregate (payment, inventory, order) |
| **Current Behavior** | `@Version` on all entities; `OptimisticLockingFailureException` thrown; domain validation prevents invalid transitions |
| **Safety** | ✅ Safe - lost updates prevented; business invariants enforced |
| **Fix** | `@Version` on: OrderEntity, PaymentEntity, InventoryItemEntity, OutboxEventEntity, OrderProjectionEntity |
| **Test** | `StaleStateTest.shouldFailFastOnStalePaymentState`, `shouldFailFastOnStaleInventoryState` |
| **Expected** | One writer wins; others fail fast with clear error; no overselling/double-approval |

---

### 12. Partial Workflow Execution (Saga)

| Aspect | Detail |
|--------|--------|
| **Trigger** | Payment gateway fails, inventory unavailable, crash between saga steps |
| **Current Behavior** | Two-phase saga: `processPayment()` creates PENDING; `completePayment()` calls gateway then updates status |
| **Safety** | ✅ Safe - PENDING state is recoverable; compensation via rejection |
| **Fix** | Saga pattern with explicit PENDING state; async completion; idempotent `completePayment` |
| **Test** | `PartialWorkflowTest.shouldNotLeavePendingPaymentIfGatewayFails`, `shouldCompensatePaymentOnGatewayFailure` |
| **Expected** | Failed gateway → payment REJECTED; event published; no money lost; retryable |

---

### 13. Network Timeout

| Aspect | Detail |
|--------|--------|
| **Trigger** | Slow Kafka broker, slow DB, slow payment gateway |
| **Current Behavior** | Timeouts configured: DB 30s, Kafka producer retries 3x, payment gateway should have client timeout |
| **Safety** | ⚠️ Partially safe - payment gateway timeout not explicitly configured |
| **Fix** | Document required timeout for `PaymentGateway` implementations; short DB transactions |
| **Test** | `NetworkTimeoutTest.shouldNotBlockIndefinitelyOnKafkaTimeout`, `shouldRespectTransactionTimeout` |
| **Expected** | Bounded wait times; no indefinite blocking; connection pool protected |

---

### 14. Worker Crash

| Aspect | Detail |
|--------|--------|
| **Trigger** | Pod kill, OOM, host failure during processing |
| **Current Behavior** | If crash in `@Transactional`: rollback (nothing persisted); If crash after commit: outbox has event, publisher recovers on restart |
| **Safety** | ✅ Safe - transactional boundaries align with crash recovery |
| **Fix** | Transactional outbox; idempotent consumers; PENDING payment state |
| **Test** | `WorkerCrashTest.shouldNotLeavePartialOrderOnCrash`, `shouldNotLeavePartialPaymentOnCrash`, `shouldHandleOutboxPublisherCrashMidBatch` |
| **Expected** | No partial state; recovery automatic on restart |

---

### 15. Application Restart

| Aspect | Detail |
|--------|--------|
| **Trigger** | Rolling deploy, config change, crash recovery, scaling |
| **Current Behavior** | Flyway migrations run; outbox publisher picks up unsent events; consumers resume from offset; idempotency prevents reprocessing |
| **Safety** | ✅ Safe - all recovery automatic |
| **Fix** | Migrations use `IF NOT EXISTS`; scheduled publisher; manual acknowledgment |
| **Test** | `ApplicationRestartTest.shouldRecoverPendingOutboxEventsOnRestart`, `shouldRecoverPendingPaymentsOnRestart`, `shouldNotReprocessAlreadyProcessedEventsOnRestart` |
| **Expected** | Zero manual intervention; all in-flight work recovers correctly |

---

## Summary Table

| # | Scenario | Current Protection | Test Coverage | Safe? |
|---|----------|-------------------|---------------|-------|
| 1 | Database unavailable | HikariCP timeout + TX rollback | ✅ | ✅ |
| 2 | Transaction rollback | Spring `@Transactional` | ✅ | ✅ |
| 3 | Kafka producer down | Transactional outbox | ✅ | ✅ |
| 4 | Kafka consumer down | Manual ack + offset commit | ✅ | ✅ |
| 5 | Duplicate delivery | Idempotency key (unique constraint) | ✅ | ✅ |
| 6 | Consumer restart | Offset commit after processing | ✅ | ✅ |
| 7 | Transient processing failure | Spring Retry (3x exponential) | ✅ | ✅ |
| 8 | Permanent processing failure | DLQ + recover | ✅ | ✅ |
| 9 | Outbox retry exhaustion | Max retries (5) + DLQ | ✅ | ✅ |
| 10 | Malformed event | Deserialization error → DLQ | ✅ | ✅ |
| 11 | Stale state | `@Version` + domain validation | ✅ | ✅ |
| 12 | Partial workflow | Saga (PENDING + async complete) | ✅ | ✅ |
| 13 | Network timeout | Configured timeouts | ⚠️ | ⚠️ |
| 14 | Worker crash | TX boundaries + outbox | ✅ | ✅ |
| 15 | Application restart | Flyway + scheduled recovery | ✅ | ✅ |

---

## Key Architectural Decisions for Resilience

1. **Transactional Outbox** (ADR-002): Event written in same DB transaction as business data → no dual-write problem
2. **Idempotent Consumers** (ADR-005): Composite key `(event_id, customer_id)` → exactly-once despite redelivery
3. **Saga for Payments** (ADR-007): PENDING state → async gateway → COMPLETE/REJECT → no long transactions
4. **Pessimistic Locking on Outbox**: `SELECT FOR UPDATE SKIP LOCKED` → multiple publisher instances safe
5. **DLQ per Failure Domain**: `dlq.order.events`, `dlq.outbox.events` → isolation, no poison pill blocking
6. **Optimistic Locking Everywhere**: `@Version` on all mutable entities → lost update prevention
7. **Manual Offset Management**: `enable.auto.commit=false` → at-least-once delivery + idempotency = exactly-once
8. **Short Transactions**: External calls outside `@Transactional` → no connection pool exhaustion

---

## Test Files Created

| Test File | Scenarios Covered |
|-----------|-------------------|
| `DatabaseUnavailableTest.java` | 1, 2 |
| `KafkaUnavailableTest.java` | 3, 4 |
| `DuplicateEventTest.java` | 5 |
| `ConsumerRestartTest.java` | 6 |
| `MessageProcessingFailureTest.java` | 7, 8 |
| `RetryExhaustionTest.java` | 9 |
| `MalformedEventTest.java` | 10 |
| `StaleStateTest.java` | 11 |
| `PartialWorkflowTest.java` | 12 |
| `NetworkTimeoutTest.java` | 13 |
| `WorkerCrashTest.java` | 14 |
| `ApplicationRestartTest.java` | 15 |

All tests use Testcontainers (real PostgreSQL + Kafka), deterministic concurrency (CountDownLatch), and eventual consistency assertions (Awaitility). No sleep-based flakiness.