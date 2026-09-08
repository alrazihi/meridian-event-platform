package com.meridian.event.infrastructure.concurrency;

import com.meridian.event.application.port.outbound.PaymentRepository;
import com.meridian.event.domain.model.Payment;
import com.meridian.event.domain.model.PaymentStatus;
import com.meridian.event.domain.model.valueobjects.Money;
import com.meridian.event.domain.model.valueobjects.PaymentId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Import(RepositoryTestConfig.class)
@DirtiesContext
@Transactional
class PaymentConcurrencyTest {

    @Autowired
    private PaymentRepository paymentRepository;

    private Payment savedPayment;

    @BeforeEach
    void setUp() {
        Payment payment = new Payment(
                PaymentId.generate(),
                "order-123",
                Money.of(new BigDecimal("100.00"), "USD"),
                "CREDIT_CARD"
        );
        savedPayment = paymentRepository.save(payment);
    }

    @Test
    void shouldPreventDoubleApprovalUnderConcurrentAccess() throws InterruptedException {
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
                    
                    Payment payment = paymentRepository.findById(savedPayment.getId())
                            .orElseThrow(() -> new IllegalStateException("Payment not found"));
                    
                    if (payment.getStatus() == PaymentStatus.PENDING) {
                        payment.approve();
                        paymentRepository.save(payment);
                        successCount.incrementAndGet();
                    } else {
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

        Payment finalPayment = paymentRepository.findById(savedPayment.getId())
                .orElseThrow(() -> new IllegalStateException("Payment not found"));

        assertThat(finalPayment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(threadCount - 1);
    }

    @Test
    void shouldPreventApproveThenRejectRace() throws InterruptedException {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger approveSuccess = new AtomicInteger(0);
        AtomicInteger rejectSuccess = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final boolean isApprove = i % 2 == 0;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    Payment payment = paymentRepository.findById(savedPayment.getId())
                            .orElseThrow(() -> new IllegalStateException("Payment not found"));
                    
                    if (payment.getStatus() == PaymentStatus.PENDING) {
                        if (isApprove) {
                            payment.approve();
                            paymentRepository.save(payment);
                            approveSuccess.incrementAndGet();
                        } else {
                            payment.reject("Race condition test");
                            paymentRepository.save(payment);
                            rejectSuccess.incrementAndGet();
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

        Payment finalPayment = paymentRepository.findById(savedPayment.getId())
                .orElseThrow(() -> new IllegalStateException("Payment not found"));

        assertThat(finalPayment.getStatus()).isIn(PaymentStatus.APPROVED, PaymentStatus.REJECTED);
        assertThat(approveSuccess.get() + rejectSuccess.get()).isEqualTo(1);
    }
}