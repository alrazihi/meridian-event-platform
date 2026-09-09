package com.meridian.event.infrastructure.security.adapter;

import com.meridian.event.application.port.outbound.AuthorizationService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SpringAuthorizationServiceAdapter implements AuthorizationService {

    @Override
    public boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            return authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        }
        return false;
    }

    @Override
    public boolean canAccessOrder(String authenticatedCustomerId, String orderCustomerId) {
        if (authenticatedCustomerId == null || orderCustomerId == null) {
            return false;
        }
        if (isAdmin()) {
            return true;
        }
        return authenticatedCustomerId.equals(orderCustomerId);
    }

    @Override
    public boolean canProcessPayment(String authenticatedCustomerId, String orderCustomerId) {
        if (authenticatedCustomerId == null || orderCustomerId == null) {
            return false;
        }
        if (isAdmin()) {
            return true;
        }
        return authenticatedCustomerId.equals(orderCustomerId);
    }
}
