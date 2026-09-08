package com.meridian.event.application.port.outbound;

import com.meridian.event.domain.model.Payment;
import com.meridian.event.domain.model.valueobjects.Money;

public interface PaymentGateway {

    ProcessingResult charge(Payment payment, Money amount);

    record ProcessingResult(boolean success, String errorMessage, String transactionId) {
        public static ProcessingResult success(String transactionId) {
            return new ProcessingResult(true, null, transactionId);
        }

        public static ProcessingResult failed(String errorMessage) {
            return new ProcessingResult(false, errorMessage, null);
        }
    }
}