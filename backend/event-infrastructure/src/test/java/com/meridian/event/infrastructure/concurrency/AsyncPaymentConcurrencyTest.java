package com.meridian.event.infrastructure.concurrency;

import com.meridian.event.application.port.inbound.ProcessPaymentUseCase;
import com.meridian.event.application.port.outbound.PaymentRepository;
import com.meridian.event.application.service.DefaultPaymentService;
import com.meridian.event.domain.model.Payment;
import com.meridian.event.domain.model.PaymentStatus;
import com.meridian.event.application.port.inbound.PlaceOrderUseCase;
import com.meridian.event.application.port.inbound.OrderLineInput;
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
import java.util.concurrent.CompletableFuture;
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
class AsyncPaymentConcurrencyTest {

    @Autowired
    private ProcessPaymentUseCase processPaymentUseCase;

    @Autowired
    private DefaultPaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PlaceOrderUseCase placeOrderUseCase;

    @Autowired
    private OrderLineInput orderLineInput;

    private String orderId;

    @BeforeEach
    void setUp() {
        var order = placeOrderUseCase.placeOrder("customer-concurrency", List.of(new OrderLineInput("SKU-CONCURRENCY", 1, 100.00)), "customer-concurrency");
        orderId = order.getId().value();
    }

    @Test
    void shouldHandleConcurrentAsyncCompletion() throws InterruptedException {
        int threadCount = 10;

        Payment pendingPayment = processPaymentUseCase.processPayment(orderId, 100.00, "CREDIT_CARD", "customer-concurrency");
        assertThat(pendingPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    CompletableFuture<Void> future = paymentService.processPaymentAsync(orderId, 100.00, "CREDIT_CARD", "customer-concurrency");
                    future.join();
                    successCount.incrementAndGet();
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

        Payment finalPayment = paymentRepository.findById(pendingPayment.getId()).orElseThrow();
        assertThat(finalPayment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(finalPayment.getTransactionId()).isNotNull();
        assertThat(successCount.get() + failureCount.get()).isEqualTo(threadCount);
    }

    @Test
    void shouldNotAllowDuplicateAsyncCompletion() throws InterruptedException {
        int threadCount = 5;

        Payment pendingPayment = processPaymentUseCase.processPayment(orderId, 100.00, "CREDIT_CARD", "customer-concurrency");

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger completedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    paymentService.completePayment(pendingPayment.getId().value(), "customer-concurrency");
                    completedCount.incrementAndGet();
                } catch (Exception e) {
                    // Expected for duplicates
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertThat(endLatch.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        Payment finalPayment = paymentRepository.findById(pendingPayment.getId()).orElseThrow();
        assertThat(finalPayment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(completedCount.get()).isEqualTo(1); // Only one should succeed
    }
}
