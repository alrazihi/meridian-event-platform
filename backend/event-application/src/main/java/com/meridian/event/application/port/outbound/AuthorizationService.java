package com.meridian.event.application.port.outbound;

public interface AuthorizationService {
    boolean isAdmin();
    boolean canAccessOrder(String authenticatedCustomerId, String orderCustomerId);
    boolean canProcessPayment(String authenticatedCustomerId, String orderCustomerId);
}
