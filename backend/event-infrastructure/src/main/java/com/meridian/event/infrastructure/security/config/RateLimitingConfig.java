package com.meridian.event.infrastructure.security.config;

import com.github.bucket4j.Bandwidth;
import com.github.bucket4j.Bucket;
import com.github.bucket4j.Refill;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
                // Use IP + user (from JWT if available) for rate limiting
                String ip = request.getRemoteAddr();
                String authHeader = request.getHeader("Authorization");
                return ip + ":" + (authHeader != null ? authHeader.substring(0, Math.min(20, authHeader.length())) : "anonymous");
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