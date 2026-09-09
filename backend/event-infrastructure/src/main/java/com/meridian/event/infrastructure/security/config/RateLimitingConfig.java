package com.meridian.event.infrastructure.security.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class RateLimitingConfig {

    private static final int WINDOW_SECONDS = 60;
    private static final int MAX_REQUESTS = 100;

    private static class RateLimitBucket {
        final AtomicInteger count = new AtomicInteger(0);
        Instant windowStart = Instant.now();
    }

    private final Map<String, RateLimitBucket> buckets = new ConcurrentHashMap<>();

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> rateLimitingFilter() {
        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                    throws ServletException, IOException {

                String key = resolveKey(request);
                RateLimitBucket bucket = buckets.computeIfAbsent(key, k -> new RateLimitBucket());

                Instant now = Instant.now();
                if (now.isAfter(bucket.windowStart.plusSeconds(WINDOW_SECONDS))) {
                    bucket.count.set(0);
                    bucket.windowStart = now;
                }

                if (bucket.count.incrementAndGet() <= MAX_REQUESTS) {
                    filterChain.doFilter(request, response);
                } else {
                    response.setStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS.value());
                    response.setContentType("application/json");
                    response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded\"}");
                }
            }

            private String resolveKey(HttpServletRequest request) {
                String ip = request.getRemoteAddr();
                String userId = getAuthenticatedUserId();
                return ip + ":" + (userId != null ? userId : "anonymous");
            }

            private String getAuthenticatedUserId() {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
                    String customerId = jwt.getClaimAsString("customer_id");
                    if (customerId != null) {
                        return customerId;
                    }
                    return jwt.getSubject();
                }
                return null;
            }
        });
        registration.addUrlPatterns("/api/v1/*");
        registration.setOrder(1);
        return registration;
    }
}