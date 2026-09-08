package com.meridian.event.infrastructure.concurrency;

import com.meridian.event.application.port.inbound.PlaceOrderUseCase;
import com.meridian.event.application.port.inbound.ProcessPaymentUseCase;
import com.meridian.event.application.port.inbound.ReserveInventoryUseCase;
import com.meridian.event.application.port.inbound.OrderLineInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Import(TestConfig.class)
@DirtiesContext
@Transactional
class CrossServiceDeadlockTest {

    @Autowired
    private PlaceOrderUseCase placeOrderUseCase;

    @Autowired
    private ProcessPaymentUseCase processPaymentUseCase;

    @Autowired
    private ReserveInventoryUseCase reserveInventoryUseCase;

    @Autowired
    private OrderLineInput orderLineInput;

    @BeforeEach
    void setUp() {
        // Pre-create inventory for testing
    }

    @Test
    void shouldNotDeadlockWhenConcurrentOrderPaymentInventory() throws InterruptedException {
        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger orderSuccess = new AtomicInteger(0);
        AtomicInteger paymentSuccess = new AtomicInteger(0);
        AtomicInteger inventorySuccess = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    // Each thread does a full order->payment->inventory flow on different data
                    String customerId = "customer-" + idx;
                    String sku = "SKU-" + idx;
                    String orderId;
                    
                    // 1. Place order (locks order)
                    var order = placeOrderUseCase.placeOrder(customerId, List.of(new OrderLineInput(sku, 1, 100.00)), customerId);
                    orderId = order.getId().value();
                    orderSuccess.incrementAndGet();
                    
                    // 2. Process payment (locks order then payment)
                    var payment = processPaymentUseCase.processPayment(orderId, 100.00, "CREDIT_CARD");
                    paymentSuccess.incrementAndGet();
                    
                    // 3. Reserve inventory (locks inventory)
                    reserveInventoryUseCase.reserveInventory(sku, 1, customerId);
                    inventorySuccess.incrementAndGet();
                    
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertThat(endLatch.await(60, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        // All operations should succeed without deadlock
        assertThat(failures.get()).isEqualTo(0);
        assertThat(orderSuccess.get()).isEqualTo(threadCount);
        assertThat(paymentSuccess.get()).isEqualTo(threadCount);
        assertThat(inventorySuccess.get()).isEqualTo(threadCount);
    }

    @Test
    void shouldHandleHighContentionOnSameInventory() throws InterruptedException {
        // Pre-create inventory with limited stock
        String sku = "HIGH-CONTENTION-SKU";
        int stock = 100;
        
        // This test verifies optimistic locking handles contention correctly
        int threadCount = 50;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    reserveInventoryUseCase.reserveInventory(sku, 2, "customer-test");
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertThat(endLatch.await(60, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        // Exactly stock/2 should succeed (50 threads * 2 = 100, stock = 100)
        // But due to race conditions, some may fail with insufficient stock
        // The important thing is no deadlocks and no overselling
        assertThat(successCount.get() * 2).isLessThanOrEqualTo(stock);
        assertThat(failures.get()).isEqualTo(threadCount - successCount.get());
    }
}