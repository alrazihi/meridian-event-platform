package com.meridian.event.infrastructure.security.config;

import com.github.bucket4j.Bandwidth;
import com.github.bucket4j.Bucket;
import com.github.bucket4j.Refill;
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
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class RateLimitingConfig {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> rateLimitingFilter() {
        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                    throws ServletException, IOException {

                String key = resolveKey(request);
                Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket());

                if (bucket.tryConsume(1)) {
                    filterChain.doFilter(request, response);
                } else {
                    response.setStatus(HttpServletResponse.SC_TOO_MANY_REQUESTS);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded\"}");
                }
            }

            private String resolveKey(HttpServletRequest request) {
                // Use IP + authenticated user (from SecurityContext) for rate limiting
                // This avoids collisions from truncated auth headers
                String ip = request.getRemoteAddr();
                String userId = getAuthenticatedUserId();
                return ip + ":" + (userId != null ? userId : "anonymous");
            }

            private String getAuthenticatedUserId() {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
                    // Prefer customer_id claim, fall back to subject
                    String customerId = jwt.getClaimAsString("customer_id");
                    if (customerId != null) {
                        return customerId;
                    }
                    return jwt.getSubject();
                }
                return null;
            }

            private Bucket createBucket() {
                // 100 requests per minute per client
                Bandwidth limit = Bandwidth.classic(100, Refill.intervally(100, Duration.ofMinutes(1)));
                return Bucket.builder().addLimit(limit).build();
            }
        });
        registration.addUrlPatterns("/api/v1/*");
        registration.setOrder(1); // Run early
        return registration;
    }
}