package com.meridian.event.application.port.outbound;

public interface NotificationService {
    void notifyOrderConfirmed(String orderId, String customerId);
}
