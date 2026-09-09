package com.meridian.event.infrastructure.config;

import com.meridian.event.application.port.inbound.CompletePaymentUseCase;
import com.meridian.event.application.port.inbound.PlaceOrderUseCase;
import com.meridian.event.application.port.inbound.ProcessPaymentUseCase;
import com.meridian.event.application.port.inbound.QueryOrderStatusUseCase;
import com.meridian.event.application.port.inbound.ReserveInventoryUseCase;
import com.meridian.event.application.port.outbound.AuthorizationService;
import com.meridian.event.application.port.outbound.ClientIpResolver;
import com.meridian.event.application.port.outbound.EventPublisher;
import com.meridian.event.application.port.outbound.NotificationService;
import com.meridian.event.application.port.outbound.OrderRepository;
import com.meridian.event.application.port.outbound.PaymentGateway;
import com.meridian.event.application.port.outbound.PaymentRepository;
import com.meridian.event.application.port.outbound.InventoryItemRepository;
import com.meridian.event.application.service.DefaultInventoryService;
import com.meridian.event.application.service.DefaultOrderService;
import com.meridian.event.application.service.DefaultPaymentService;
import com.meridian.event.domain.service.InventoryReserver;
import com.meridian.event.domain.service.OrderValidator;
import com.meridian.event.infrastructure.payment.MockPaymentGateway;
import com.meridian.event.infrastructure.security.audit.SecurityAuditLogger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;

@TestConfiguration
public class TestConfig {

    @Bean
    public PlaceOrderUseCase placeOrderUseCase(
            OrderRepository orderRepository,
            EventPublisher eventPublisher,
            NotificationService notificationService,
            OrderValidator orderValidator,
            AuthorizationService authorizationService) {
        return new DefaultOrderService(orderRepository, eventPublisher, notificationService, orderValidator, authorizationService);
    }

    @Bean
    public QueryOrderStatusUseCase queryOrderStatusUseCase(
            OrderRepository orderRepository,
            AuthorizationService authorizationService) {
        return new DefaultOrderService(orderRepository, null, null, null, authorizationService);
    }

    @Bean
    public ProcessPaymentUseCase processPaymentUseCase(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            EventPublisher eventPublisher,
            NotificationService notificationService,
            PaymentGateway paymentGateway,
            AuthorizationService authorizationService,
            ClientIpResolver clientIpResolver,
            java.util.concurrent.Executor taskExecutor) {
        return new DefaultPaymentService(
                orderRepository, paymentRepository, eventPublisher, notificationService,
                paymentGateway, authorizationService, clientIpResolver, taskExecutor
        );
    }

    @Bean
    public ReserveInventoryUseCase reserveInventoryUseCase(
            InventoryItemRepository inventoryRepository,
            EventPublisher eventPublisher,
            AuthorizationService authorizationService,
            ClientIpResolver clientIpResolver) {
        return new DefaultInventoryService(inventoryRepository, eventPublisher, authorizationService, clientIpResolver);
    }

    @Bean
    public MockPaymentGateway mockPaymentGateway() {
        return new MockPaymentGateway();
    }

    @Bean
    public AuthorizationService authorizationService() {
        return new AuthorizationService() {
            @Override public boolean isAdmin() { return false; }
            @Override public boolean canAccessOrder(String auth, String order) { return auth == null || auth.equals(order); }
            @Override public boolean canProcessPayment(String auth, String order) { return auth == null || auth.equals(order); }
        };
    }

    @Bean
    public ClientIpResolver clientIpResolver() {
        return () -> "127.0.0.1";
    }

    @Bean
    public NotificationService notificationService() {
        return new NotificationService() {
            @Override public void notifyOrderConfirmed(String orderId, String customerId) {}
        };
    }

    @Bean
    public InventoryReserver inventoryReserver() {
        return new InventoryReserver(java.util.Map.of());
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();
            return NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic()).build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate test JWT key pair", e);
        }
    }
}
