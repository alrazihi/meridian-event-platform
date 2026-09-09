package com.meridian.event.infrastructure.persistence.adapter;

import com.meridian.event.application.port.outbound.PaymentRepository;
import com.meridian.event.domain.model.Payment;
import com.meridian.event.domain.model.valueobjects.Money;
import com.meridian.event.domain.model.valueobjects.PaymentId;
import com.meridian.event.infrastructure.config.TestConfig;
import com.meridian.event.infrastructure.persistence.adapter.RepositoryTestConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Import({RepositoryTestConfig.class, TestConfig.class})
@Transactional
class PaymentRepositoryAdapterIntegrationTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void shouldSaveAndFindPayment() {
        PaymentId paymentId = PaymentId.generate();
        Payment payment = new Payment(paymentId, "order-123", Money.of(new BigDecimal("100.00"), "USD"), "CREDIT_CARD");

        Payment saved = paymentRepository.save(payment);

        assertThat(saved.getId()).isEqualTo(paymentId);
        assertThat(saved.getOrderId()).isEqualTo("order-123");
        assertThat(saved.getAmount().value()).isEqualByComparingTo("100.00");
        assertThat(saved.getStatus()).isEqualTo(com.meridian.event.domain.model.PaymentStatus.PENDING);

        Optional<Payment> found = paymentRepository.findById(paymentId);
        assertThat(found).isPresent();
        assertThat(found.get().getOrderId()).isEqualTo("order-123");
    }

    @Test
    void shouldUpdatePaymentStatus() {
        Payment payment = new Payment(PaymentId.generate(), "order-123", Money.of(new BigDecimal("100.00"), "USD"), "CREDIT_CARD");
        Payment saved = paymentRepository.save(payment);

        saved.approve();
        Payment updated = paymentRepository.save(saved);

        assertThat(updated.getStatus()).isEqualTo(com.meridian.event.domain.model.PaymentStatus.APPROVED);
        assertThat(updated.getVersion()).isEqualTo(1L);
    }

    @Test
    void shouldReturnEmptyForNonExistentPayment() {
        Optional<Payment> found = paymentRepository.findById(PaymentId.from(UUID.randomUUID().toString()));
        assertThat(found).isEmpty();
    }
}
