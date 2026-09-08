package com.meridian.event.infrastructure.persistence.adapter;

import com.meridian.event.application.port.outbound.PaymentRepository;
import com.meridian.event.domain.model.Payment;
import com.meridian.event.domain.model.PaymentStatus;
import com.meridian.event.domain.model.valueobjects.PaymentId;
import com.meridian.event.infrastructure.persistence.jpa.PaymentEntity;
import com.meridian.event.infrastructure.persistence.mapper.PaymentMapper;
import com.meridian.event.infrastructure.persistence.repository.PaymentJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PaymentRepositoryAdapter implements com.meridian.event.application.port.outbound.PaymentRepository {

    private final PaymentJpaRepository jpaRepository;

    public PaymentRepositoryAdapter(PaymentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Payment save(Payment payment) {
        PaymentEntity entity = PaymentMapper.toEntity(payment);
        PaymentEntity saved = jpaRepository.save(entity);
        return PaymentMapper.toDomain(saved);
    }

    @Override
    public Optional<Payment> findById(PaymentId id) {
        return jpaRepository.findById(id.value())
                .map(PaymentMapper::toDomain);
    }

    @Override
    public boolean existsByOrderIdAndStatus(String orderId, PaymentStatus status) {
        return jpaRepository.existsByOrderIdAndStatus(orderId, status.name());
    }
}