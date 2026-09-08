package com.meridian.event.infrastructure.persistence.mapper;

import com.meridian.event.domain.model.Payment;
import com.meridian.event.domain.model.valueobjects.Money;
import com.meridian.event.domain.model.valueobjects.PaymentId;
import com.meridian.event.infrastructure.persistence.jpa.PaymentEntity;

import java.math.BigDecimal;
import java.time.Instant;

public class PaymentMapper {

    public static PaymentEntity toEntity(Payment payment) {
        PaymentEntity entity = new PaymentEntity();
        entity.setId(payment.getId().value());
        entity.setOrderId(payment.getOrderId());
        entity.setAmount(payment.getAmount().value());
        entity.setPaymentMethod(payment.getPaymentMethod());
        entity.setStatus(payment.getStatus().name());
        entity.setCreatedAt(payment.getCreatedAt());
        entity.setUpdatedAt(payment.getUpdatedAt());
        return entity;
    }

    public static Payment toDomain(PaymentEntity entity) {
        Payment payment = new Payment(
                PaymentId.from(entity.getId()),
                entity.getOrderId(),
                Money.of(entity.getAmount(), "USD"),
                entity.getPaymentMethod()
        );

        payment.setAmount(Money.of(entity.getAmount(), "USD"));
        payment.setStatus(com.meridian.event.domain.model.PaymentStatus.valueOf(entity.getStatus()));
        payment.setVersion(0L);
        payment.setCreatedAt(entity.getCreatedAt());
        payment.setUpdatedAt(entity.getUpdatedAt());
        return payment;
    }
}