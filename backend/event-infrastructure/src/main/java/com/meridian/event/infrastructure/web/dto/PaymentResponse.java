package com.meridian.event.infrastructure.web.dto;

import com.meridian.event.domain.model.Payment;
import com.meridian.event.domain.model.valueobjects.PaymentId;

import java.math.BigDecimal;

public record PaymentResponse(
        String paymentId,
        String orderId,
        BigDecimal amount,
        String status,
        String paymentMethod
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId().value(),
                payment.getOrderId(),
                payment.getAmount().value(),
                payment.getStatus().name(),
                payment.getPaymentMethod()
        );
    }
}
