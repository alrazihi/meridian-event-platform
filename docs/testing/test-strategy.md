# Test Strategy

## Test Pyramid

```
        /\
       /  \     E2E (10%)
      /____\    - Full stack with Testcontainers
     /      \
    /________\  Integration (30%)
   /          \ - Repository + Kafka + REST
  /____________\ Unit (60%)
                 - Domain services, validators
```

## Unit Tests

**Scope:** Domain services, validators, value objects

**Tools:** JUnit 5, Mockito, AssertJ

**Coverage Target:** 90%+ line coverage on domain module

**Example:**
```java
class OrderValidatorTest {
    @Test
    void shouldRejectOrderWithNoLines() {
        OrderValidator validator = new OrderValidator();
        Order order = Order.create("customer-123", List.of());
        OrderValidator.ValidationResult result = validator.validate(order);
        assertThat(result.valid()).isFalse();
        assertThat(result.errorMessage()).contains("at least one line item");
    }
}
```

## Integration Tests

### Repository Tests
```java
@Testcontainers
class OrderRepositoryIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void shouldPersistAndRetrieveOrder() {
        // Flyway runs migrations automatically
        Order order = Order.create("customer-123", lines);
        orderRepository.save(order);
        Optional<Order> found = orderRepository.findById(order.getId());
        assertThat(found).isPresent();
    }
}
```

### Kafka Integration Tests
```java
@Testcontainers
class KafkaEventPublisherIntegrationTest {
    @Container
    static KafkaContainer kafka = new KafkaContainer("confluentinc/cp-kafka:7.5.0");

    @Test
    void shouldPublishEventWhenOrderPlaced() {
        // Verify event appears on order.events topic
    }
}
```

### End-to-End Tests
```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class OrderWorkflowE2ETest {
    @Test
    void shouldCompleteOrderWorkflow() {
        // 1. Place order
        // 2. Verify event published to Kafka
        // 3. Process payment
        // 4. Reserve inventory
        // 5. Verify final state
    }
}
```

## Contract Tests

- **OpenAPI contract testing:** Spring Cloud Contract
- **Kafka contract testing:** Schema validation against Avro schemas
- **Consumer-driven contracts:** For downstream consumers

## Performance Tests

- **JMH benchmarks** for hot paths: order validation, payment processing
- **Gatling** load tests for order placement endpoint (target: 500 req/s)
- **Kafka producer throughput** testing

## Failure Scenario Tests

- Kafka broker unavailable → verify outbox retry
- Duplicate event → verify idempotency
- Poison message → verify DLQ routing
- Database timeout → verify transaction rollback
- Consumer crash → verify offset recovery

## Quality Gates

| Metric | Target |
|--------|--------|
| Unit test coverage | >= 90% |
| Integration test coverage | >= 70% |
| Mutation testing score | >= 80% |
| Build time | < 10 minutes |
| Test execution time | < 5 minutes (unit), < 15 minutes (integration) |
