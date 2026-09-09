package com.meridian.event.infrastructure.resilience;

import com.meridian.event.application.port.outbound.PaymentGateway;
import com.meridian.event.domain.model.Payment;
import com.meridian.event.domain.model.valueobjects.Money;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class CircuitBreakerPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerPaymentGateway.class);
    private final PaymentGateway delegate;

    public CircuitBreakerPaymentGateway(PaymentGateway delegate) {
        this.delegate = delegate;
    }

    @Override
    @CircuitBreaker(name = "paymentGateway", fallbackMethod = "fallbackCharge")
    @TimeLimiter(name = "paymentGateway")
    public ProcessingResult charge(Payment payment, Money amount) {
        return delegate.charge(payment, amount);
    }

    private ProcessingResult fallbackCharge(Payment payment, Money amount, Exception e) {
        log.error("Payment gateway fallback triggered", e);
        return ProcessingResult.failed("Payment gateway unavailable: " + e.getMessage());
    }
}
