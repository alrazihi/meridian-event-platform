package com.meridian.event.infrastructure.web.filter;

import com.meridian.event.infrastructure.persistence.jpa.IdempotencyKeyEntity;
import com.meridian.event.infrastructure.persistence.repository.IdempotencyKeyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Component
public class IdempotencyKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyFilter.class);
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final int KEY_TTL_HOURS = 24;

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    public IdempotencyKeyFilter(IdempotencyKeyRepository idempotencyKeyRepository, ObjectMapper objectMapper) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (idempotencyKey == null || idempotencyKey.isBlank() || !"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String keyHash = hashKey(idempotencyKey);
        String endpoint = request.getRequestURI();
        Instant now = Instant.now();

        var existing = idempotencyKeyRepository.findByKeyHashAndEndpointAndExpiresAtAfter(keyHash, endpoint, now);
        if (existing.isPresent()) {
            log.info("Idempotent request detected keyHash={} endpoint={} status={}", keyHash, endpoint, existing.get().getStatusCode());
            response.setStatus(existing.get().getStatusCode());
            response.setContentType("application/json");
            response.getWriter().write(existing.get().getResponseBody());
            return;
        }

        IdempotencyKeyFilterWrapper wrapper = new IdempotencyKeyFilterWrapper(request, response);
        filterChain.doFilter(request, wrapper);

        if (wrapper.isCommitted()) {
            saveIdempotencyKey(keyHash, endpoint, wrapper.getResponseBody(), wrapper.getStatus(), now);
        }
    }

    private void saveIdempotencyKey(String keyHash, String endpoint, String responseBody, int status, Instant now) {
        try {
            IdempotencyKeyEntity entity = new IdempotencyKeyEntity();
            entity.setKeyHash(keyHash);
            entity.setEndpoint(endpoint);
            entity.setResponseBody(responseBody);
            entity.setStatusCode(status);
            entity.setCreatedAt(now);
            entity.setExpiresAt(now.plus(KEY_TTL_HOURS, ChronoUnit.HOURS));
            idempotencyKeyRepository.save(entity);
        } catch (Exception e) {
            log.error("Failed to save idempotency key", e);
        }
    }

    private String hashKey(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash idempotency key", e);
        }
    }
}
