package com.meridian.event.infrastructure.web.adapter;

import com.meridian.event.application.port.outbound.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ServletClientIpResolver implements ClientIpResolver {

    private final HttpServletRequest request;

    public ServletClientIpResolver(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public String resolveClientIp() {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
