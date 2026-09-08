package com.meridian.event.infrastructure.security.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Component
public class SecurityAuditLogger {

    private static final Logger auditLog = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int HMAC_TRUNCATE_LENGTH = 12;

    private final Mac hmac;

    public SecurityAuditLogger(@Value("${AUDIT_HMAC_SECRET:change-me-in-production}") String hmacSecret) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(
                    hmacSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            this.hmac = Mac.getInstance(HMAC_ALGORITHM);
            this.hmac.init(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to initialize HMAC for audit logging", e);
        }
    }

    public void logAuthenticationSuccess(String customerId, String ip, String endpoint) {
        auditLog.info("AUTH_SUCCESS customerId={} ip={} endpoint={} timestamp={}",
                pseudonymize(customerId), ip, endpoint, Instant.now());
    }

    public void logAuthenticationFailure(String customerId, String ip, String endpoint, String reason) {
        auditLog.warn("AUTH_FAILURE customerId={} ip={} endpoint={} reason={} timestamp={}",
                pseudonymize(customerId), ip, endpoint, reason, Instant.now());
    }

    public void logAuthorizationDenied(String customerId, String ip, String endpoint, String resource, String reason) {
        auditLog.warn("AUTHZ_DENIED customerId={} ip={} endpoint={} resource={} reason={} timestamp={}",
                pseudonymize(customerId), ip, endpoint, resource, reason, Instant.now());
    }

    public void logOrderAccess(String customerId, String ip, String orderId, String action) {
        auditLog.info("ORDER_ACCESS customerId={} ip={} orderId={} action={} timestamp={}",
                pseudonymize(customerId), ip, pseudonymize(orderId), action, Instant.now());
    }

    public void logPaymentAttempt(String customerId, String ip, String orderId, String amount, String result) {
        auditLog.info("PAYMENT_ATTEMPT customerId={} ip={} orderId={} amount={} result={} timestamp={}",
                pseudonymize(customerId), ip, pseudonymize(orderId), amount, result, Instant.now());
    }

    public void logInventoryReservation(String customerId, String ip, String sku, int quantity, String result) {
        auditLog.info("INVENTORY_RESERVATION customerId={} ip={} sku={} quantity={} result={} timestamp={}",
                pseudonymize(customerId), ip, sku, quantity, result, Instant.now());
    }

    public void logAdminAction(String adminId, String ip, String action, String resource, Map<String, Object> details) {
        auditLog.info("ADMIN_ACTION adminId={} ip={} action={} resource={} details={} timestamp={}",
                pseudonymize(adminId), ip, action, resource, details, Instant.now());
    }

    public void logRateLimitExceeded(String ip, String endpoint) {
        auditLog.warn("RATE_LIMIT_EXCEEDED ip={} endpoint={} timestamp={}", ip, endpoint, Instant.now());
    }

    private String pseudonymize(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return "****";
        }
        // Use HMAC-SHA256 with secret key, truncate to 12 chars for readability
        byte[] hash = hmac.doFinal(identifier.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash).substring(0, HMAC_TRUNCATE_LENGTH);
    }
}