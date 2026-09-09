package com.meridian.event.application.service;

import com.meridian.event.application.port.outbound.PaymentGateway;
import com.meridian.event.domain.model.Payment;
import com.meridian.event.domain.model.valueobjects.Money;

import java.util.HashMap;
import java.util.Map;

public class MockPaymentGateway implements PaymentGateway {

    private final Map<String, ProcessingResult> results = new HashMap<>();

    public void setResult(String paymentId, ProcessingResult result) {
        results.put(paymentId, result);
    }

    @Override
    public ProcessingResult charge(Payment payment, Money amount) {
        ProcessingResult result = results.get(payment.getId().value());
        if (result != null) {
            return result;
        }
        return ProcessingResult.success("txn-" + System.currentTimeMillis());
    }
}
