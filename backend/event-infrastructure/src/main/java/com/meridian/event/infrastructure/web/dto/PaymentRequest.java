package com.meridian.event.infrastructure.web.dto;

import com.meridian.event.domain.model.Payment;

import java.math.BigDecimal;

public record PaymentRequest(
        String orderId,
        double amount,
        String paymentMethod
) {
}
