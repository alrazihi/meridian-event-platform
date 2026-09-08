package com.meridian.event.application.port.inbound;

import com.meridian.event.domain.model.Payment;

public interface ProcessPaymentUseCase {
    Payment processPayment(String orderId, double amount, String paymentMethod, String authenticatedCustomerId);
}
