package com.meridian.event.infrastructure.config;

import com.meridian.event.application.port.outbound.AuthorizationService;
import com.meridian.event.application.port.outbound.PaymentGateway;
import com.meridian.event.infrastructure.payment.MockPaymentGateway;
import com.meridian.event.infrastructure.resilience.CircuitBreakerPaymentGateway;
import com.meridian.event.infrastructure.security.adapter.SpringAuthorizationServiceAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class InfrastructureConfig {

    @Bean
    public AuthorizationService authorizationService() {
        return new SpringAuthorizationServiceAdapter();
    }

    @Bean
    @Primary
    public com.meridian.event.application.port.outbound.PaymentGateway paymentGateway(
            MockPaymentGateway mockPaymentGateway) {
        return new CircuitBreakerPaymentGateway(mockPaymentGateway);
    }
}
