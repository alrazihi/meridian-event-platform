package com.meridian.event.infrastructure.persistence.jpa;

import jakarta.persistence.*;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKeyEntity {

    @Id
    @Column(name = "key_hash", nullable = false, updatable = false)
    private String keyHash;

    @Column(name = "endpoint", nullable = false)
    private String endpoint;

    @Column(name = "response_body", columnDefinition = "jsonb")
    private String responseBody;

    @Column(name = "status_code", nullable = false)
    private int statusCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private java.time.Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private java.time.Instant expiresAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = java.time.Instant.now();
    }

    public String getKeyHash() { return keyHash; }
    public void setKeyHash(String keyHash) { this.keyHash = keyHash; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
    public int getStatusCode() { return statusCode; }
    public void setStatusCode(int statusCode) { this.statusCode = statusCode; }
    public java.time.Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.Instant createdAt) { this.createdAt = createdAt; }
    public java.time.Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(java.time.Instant expiresAt) { this.expiresAt = expiresAt; }
}
