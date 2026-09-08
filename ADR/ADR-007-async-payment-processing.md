# ADR-007: Async Payment Processing with Saga Pattern

## Status
Accepted

## Context
The original payment processing implementation executed the external payment gateway call within the same database transaction as the business logic. This creates several problems:

1. **Long-running transactions**: External payment APIs (Stripe, Adyen, etc.) can take 2-30+ seconds to respond, holding database connections and locks
2. **Connection pool exhaustion**: Under load, long transactions exhaust the HikariCP connection pool
3. **Lock contention**: Row locks on payment/order tables held during external calls block concurrent operations
4. **Timeout coupling**: Database transaction timeout must exceed payment gateway timeout
5. **Rollback complexity**: If gateway succeeds but DB commit fails, we have charged customer without recording payment

## Decision
Split payment processing into a **saga** with two short transactions:

1. **Transaction 1 (Initiate)**: Validate, create PENDING payment record, return immediately
2. **External Call**: Invoke payment gateway asynchronously (outside any transaction)
3. **Transaction 2 (Complete)**: Update payment status (APPROVED/REJECTED), persist transaction ID, publish event

## Implementation

### New Port
```java
public interface PaymentGateway {
    ProcessingResult charge(Payment payment, Money amount);
    record ProcessingResult(boolean success, String errorMessage, String transactionId) {}
}
```

### Service Changes
```java
@Transactional
public Payment processPayment(...) {  // Transaction 1
    // Validate, create PENDING payment
    return paymentRepository.save(payment);
}

@Transactional  
public void completePayment(String paymentId, String customerId) {  // Transaction 2
    Payment payment = paymentRepository.findById(paymentId);
    ProcessingResult result = paymentGateway.charge(payment, payment.getAmount());
    if (result.success()) {
        payment.approve();
        payment.setTransactionId(result.transactionId());
    } else {
        payment.reject(result.errorMessage());
    }
    paymentRepository.save(payment);
    eventPublisher.publish(new PaymentProcessedEvent(...));
}

public CompletableFuture<Void> processPaymentAsync(...) {
    Payment pending = processPayment(...);
    return CompletableFuture.runAsync(() -> completePayment(pending.getId(), customerId));
}
```

## Concurrency Guarantees
- **Optimistic locking** (`@Version`) on PaymentEntity prevents lost updates
- **Domain validation** in `approve()`/`reject()` ensures only PENDING → APPROVED/REJECTED
- **Idempotent completion**: Calling `completePayment` twice fails fast on second call (status != PENDING)
- **Exactly-once semantics**: Outbox pattern ensures event published exactly once per approval

## Trade-offs
| Aspect | Before | After |
|--------|--------|-------|
| Transaction duration | 2-30s (external call) | <50ms each |
| Connection pool pressure | High | Low |
| Lock hold time | Long | Short |
| Complexity | Simple | Saga orchestrator needed |
| Failure handling | Automatic rollback | Compensation logic (retry DLQ) |
| Consistency | Strong (ACID) | Eventual (BASE) |

## Alternatives Considered
1. **Synchronous with long timeout**: Rejected - doesn't solve connection pool/lock issues
2. **Polling status**: Rejected - adds complexity, doesn't eliminate long transaction for initial call
3. **Full event-driven (no REST)**: Rejected - overkill for current scale, REST API still needed

## Consequences
- Callers must handle asynchronous completion (polling or webhook)
- Payment status temporarily PENDING (UI must reflect this)
- Need compensation for gateway failures (retry with exponential backoff, then DLQ)
- Added `transaction_id` column to payments table for reconciliation

## Related
- ADR-002: Outbox Pattern (used for event publishing in Transaction 2)
- ADR-005: Idempotent Consumers (ensures exactly-once event processing)
- ADR-006: Dead Letter Queue (handles failed gateway retries)