package com.meridian.event.infrastructure.messaging.notification;

import com.meridian.event.application.port.outbound.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NotificationServiceAdapter implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceAdapter.class);

    @Override
    public void notifyOrderConfirmed(String orderId, String customerId) {
        log.info("Notification: Order {} confirmed for customer {}", orderId, customerId);
    }
}