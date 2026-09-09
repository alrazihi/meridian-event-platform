package com.meridian.event.application.port.inbound;

import com.meridian.event.domain.model.Payment;

public interface CompletePaymentUseCase {
    void completePayment(String paymentId, String authenticatedCustomerId);
}
