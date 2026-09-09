package com.meridian.event.infrastructure.persistence.adapter;

import com.meridian.event.application.port.outbound.OrderRepository;
import com.meridian.event.domain.model.Order;
import com.meridian.event.domain.model.OrderLine;
import com.meridian.event.domain.model.valueobjects.Money;
import com.meridian.event.domain.model.valueobjects.OrderId;
import com.meridian.event.domain.model.valueobjects.Sku;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Import(RepositoryTestConfig.class)
@Transactional
class OptimisticLockingIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void shouldDetectConcurrentModification() throws InterruptedException {
        // Create initial order
        Order order = new Order(OrderId.generate(), "customer-123", List.of(
                new OrderLine(Sku.of("SKU-1"), 1, Money.of(new BigDecimal("10.00"), "USD"))
        ));
        Order saved = orderRepository.save(order);
        String orderId = saved.getId().value();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Order current = orderRepository.findById(OrderId.from(orderId)).orElseThrow();
                    current.confirm();
                    orderRepository.save(current);
                    successCount.incrementAndGet();
                } catch (OptimisticLockingFailureException e) {
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly one should succeed, rest should fail with optimistic locking
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(threadCount - 1);

        // Final state should be CONFIRMED
        Order finalOrder = orderRepository.findById(OrderId.from(orderId)).orElseThrow();
        assertThat(finalOrder.getStatus()).isEqualTo(com.meridian.event.domain.model.OrderStatus.CONFIRMED);
    }
}
