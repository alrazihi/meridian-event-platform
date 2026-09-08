package com.meridian.event.infrastructure.security.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class SecurityAuditLogger {

    private static final Logger auditLog = LoggerFactory.getLogger("SECURITY_AUDIT");

    public void logAuthenticationSuccess(String customerId, String ip, String endpoint) {
        auditLog.info("AUTH_SUCCESS customerId={} ip={} endpoint={} timestamp={}", 
                maskCustomerId(customerId), ip, endpoint, Instant.now());
    }

    public void logAuthenticationFailure(String customerId, String ip, String endpoint, String reason) {
        auditLog.warn("AUTH_FAILURE customerId={} ip={} endpoint={} reason={} timestamp={}", 
                maskCustomerId(customerId), ip, endpoint, reason, Instant.now());
    }

    public void logAuthorizationDenied(String customerId, String ip, String endpoint, String resource, String reason) {
        auditLog.warn("AUTHZ_DENIED customerId={} ip={} endpoint={} resource={} reason={} timestamp={}", 
                maskCustomerId(customerId), ip, endpoint, resource, reason, Instant.now());
    }

    public void logOrderAccess(String customerId, String ip, String orderId, String action) {
        auditLog.info("ORDER_ACCESS customerId={} ip={} orderId={} action={} timestamp={}", 
                maskCustomerId(customerId), ip, maskOrderId(orderId), action, Instant.now());
    }

    public void logPaymentAttempt(String customerId, String ip, String orderId, String amount, String result) {
        auditLog.info("PAYMENT_ATTEMPT customerId={} ip={} orderId={} amount={} result={} timestamp={}", 
                maskCustomerId(customerId), ip, maskOrderId(orderId), amount, result, Instant.now());
    }

    public void logInventoryReservation(String customerId, String ip, String sku, int quantity, String result) {
        auditLog.info("INVENTORY_RESERVATION customerId={} ip={} sku={} quantity={} result={} timestamp={}", 
                maskCustomerId(customerId), ip, sku, quantity, result, Instant.now());
    }

    public void logAdminAction(String adminId, String ip, String action, String resource, Map<String, Object> details) {
        auditLog.info("ADMIN_ACTION adminId={} ip={} action={} resource={} details={} timestamp={}", 
                maskCustomerId(adminId), ip, action, resource, details, Instant.now());
    }

    public void logRateLimitExceeded(String ip, String endpoint) {
        auditLog.warn("RATE_LIMIT_EXCEEDED ip={} endpoint={} timestamp={}", ip, endpoint, Instant.now());
    }

    private String maskCustomerId(String customerId) {
        if (customerId == null || customerId.length() <= 4) return "****";
        return customerId.substring(0, 2) + "****" + customerId.substring(customerId.length() - 2);
    }

    private String maskOrderId(String orderId) {
        if (orderId == null || orderId.length() <= 8) return "****";
        return orderId.substring(0, 4) + "****" + orderId.substring(orderId.length() - 4);
    }
}