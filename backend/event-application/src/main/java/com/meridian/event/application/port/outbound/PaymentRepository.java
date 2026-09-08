package com.meridian.event.application.port.outbound;

import com.meridian.event.domain.model.Payment;
import com.meridian.event.domain.model.valueobjects.PaymentId;

import java.util.Optional;

public interface PaymentRepository {
    Payment save(Payment payment);
    Optional<Payment> findById(PaymentId id);
}