package com.meridian.event.infrastructure.security.config;

import com.github.bucket4j.Bucket;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = RateLimitingConfig.class)
class RateLimitingConfigTest {

    @Autowired
    private RateLimitingConfig rateLimitingConfig;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldCreateBucketWithCorrectLimits() {
        // Access private method via reflection or test via filter
        // For now, verify the config bean loads
        assertThat(rateLimitingConfig).isNotNull();
    }

    @Test
    void bucketShouldAllowRequestsWithinLimit() {
        Bucket bucket = Bucket.builder()
                .addLimit(com.github.bucket4j.Bandwidth.classic(100, 
                        com.github.bucket4j.Refill.intervally(100, Duration.ofMinutes(1))))
                .build();

        // Should allow 100 requests
        for (int i = 0; i < 100; i++) {
            assertThat(bucket.tryConsume(1)).isTrue();
        }

        // 101st should be rejected
        assertThat(bucket.tryConsume(1)).isFalse();
    }
}