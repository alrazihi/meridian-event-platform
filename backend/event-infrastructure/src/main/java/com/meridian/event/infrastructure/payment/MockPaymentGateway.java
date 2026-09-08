package com.meridian.event.infrastructure.payment;

import com.meridian.event.application.port.outbound.PaymentGateway;
import com.meridian.event.domain.model.Payment;
import com.meridian.event.domain.model.valueobjects.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class MockPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);

    @Override
    public ProcessingResult charge(Payment payment, Money amount) {
        log.info("Processing payment {} for amount {} via mock gateway", payment.getId().value(), amount);

        if (!payment.getAmount().equals(amount)) {
            return ProcessingResult.failed("Payment amount mismatch");
        }

        if (amount.value().compareTo(Money.zero().value()) <= 0) {
            return ProcessingResult.failed("Payment amount must be positive");
        }

        String transactionId = "txn_" + UUID.randomUUID().toString().substring(0, 8);
        return ProcessingResult.success(transactionId);
    }

    public CompletableFuture<ProcessingResult> chargeAsync(Payment payment, Money amount) {
        return CompletableFuture.supplyAsync(() -> charge(payment, amount));
    }
}