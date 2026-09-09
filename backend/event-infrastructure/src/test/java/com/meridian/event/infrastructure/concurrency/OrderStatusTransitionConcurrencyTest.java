package com.meridian.event.infrastructure.concurrency;

import com.meridian.event.application.port.outbound.OrderRepository;
import com.meridian.event.domain.model.Order;
import com.meridian.event.domain.model.OrderLine;
import com.meridian.event.domain.model.OrderStatus;
import com.meridian.event.domain.model.valueobjects.Money;
import com.meridian.event.domain.model.valueobjects.OrderId;
import com.meridian.event.domain.model.valueobjects.Sku;
import com.meridian.event.infrastructure.config.TestConfig;
import com.meridian.event.infrastructure.persistence.adapter.RepositoryTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
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

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Import({RepositoryTestConfig.class, TestConfig.class})
@DirtiesContext
@Transactional
class OrderStatusTransitionConcurrencyTest {

    @Autowired
    private OrderRepository orderRepository;

    private OrderId orderId;

    @BeforeEach
    void setUp() {
        orderId = OrderId.generate();
        List<OrderLine> lines = List.of(
                new OrderLine(Sku.of("SKU-1"), 2, Money.of(new BigDecimal("10.00"), "USD"))
        );
        Order order = new Order(orderId, "customer-123", lines);
        orderRepository.save(order);
    }

    @Test
    void shouldPreventConcurrentConfirmAndCancel() throws InterruptedException {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger confirmSuccess = new AtomicInteger(0);
        AtomicInteger cancelSuccess = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final boolean isConfirm = i % 2 == 0;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    Order order = orderRepository.findById(orderId)
                            .orElseThrow(() -> new IllegalStateException("Order not found"));
                    
                    if (order.getStatus() == OrderStatus.CREATED) {
                        if (isConfirm) {
                            order.confirm();
                            orderRepository.save(order);
                            confirmSuccess.incrementAndGet();
                        } else {
                            order.cancel("Race test");
                            orderRepository.save(order);
                            cancelSuccess.incrementAndGet();
                        }
                    } else {
                        failures.incrementAndGet();
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertThat(endLatch.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        Order finalOrder = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("Order not found"));

        assertThat(finalOrder.getStatus()).isIn(OrderStatus.CONFIRMED, OrderStatus.CANCELLED);
        assertThat(confirmSuccess.get() + cancelSuccess.get()).isEqualTo(1);
    }

    @Test
    void shouldPreventConcurrentAddLineAfterConfirm() throws InterruptedException {
        // First confirm the order
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.confirm();
        orderRepository.save(order);

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    Order o = orderRepository.findById(orderId)
                            .orElseThrow(() -> new IllegalStateException("Order not found"));
                    
                    try {
                        o.addLine(Sku.of("SKU-NEW"), 1, Money.of(new BigDecimal("5.00"), "USD"));
                        orderRepository.save(o);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertThat(endLatch.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        Order finalOrder = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("Order not found"));

        assertThat(finalOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(finalOrder.getLines()).hasSize(1); // Original line only
        assertThat(successCount.get()).isEqualTo(0);
        assertThat(failureCount.get()).isEqualTo(threadCount);
    }

    @Test
    void shouldAllowConcurrentReadsWhileWriting() throws InterruptedException {
        int readerCount = 20;
        int writerCount = 5;
        
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(readerCount + writerCount);
        ExecutorService executor = Executors.newFixedThreadPool(readerCount + writerCount);
        AtomicInteger readCount = new AtomicInteger(0);
        AtomicInteger writeSuccess = new AtomicInteger(0);

        for (int i = 0; i < readerCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Order order = orderRepository.findById(orderId)
                            .orElseThrow(() -> new IllegalStateException("Order not found"));
                    readCount.incrementAndGet();
                    // Verify consistent state
                    assertThat(order.getId()).isEqualTo(orderId);
                } catch (Exception e) {
                    // Ignore
                } finally {
                    endLatch.countDown();
                }
            });
        }

        for (int i = 0; i < writerCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Order order = orderRepository.findById(orderId)
                            .orElseThrow(() -> new IllegalStateException("Order not found"));
                    if (order.getStatus() == OrderStatus.CREATED) {
                        order.confirm();
                        orderRepository.save(order);
                        writeSuccess.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Ignore
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertThat(endLatch.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(readCount.get()).isEqualTo(readerCount);
        assertThat(writeSuccess.get()).isLessThanOrEqualTo(1);
    }
}
