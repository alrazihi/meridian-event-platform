package com.meridian.event.infrastructure.security.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.filter.OncePerRequestFilter;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = RateLimitingConfig.class)
class RateLimitingConfigTest {

    @Autowired
    private RateLimitingConfig rateLimitingConfig;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldCreateRateLimitingFilter() {
        assertThat(rateLimitingConfig).isNotNull();
        assertThat(rateLimitingConfig.rateLimitingFilter()).isNotNull();
        assertThat(rateLimitingFilter().getFilter()).isInstanceOf(OncePerRequestFilter.class);
    }

    @Test
    void shouldRegisterFilterForApiPaths() {
        var registration = rateLimitingConfig.rateLimitingFilter();
        assertThat(registration.getUrlPatterns()).contains("/api/v1/*");
        assertThat(registration.getOrder()).isEqualTo(1);
    }

    private org.springframework.boot.web.servlet.FilterRegistrationBean<OncePerRequestFilter> rateLimitingFilter() {
        return rateLimitingConfig.rateLimitingFilter();
    }
}
