package com.meridian.event.application.service;

import com.meridian.event.application.port.outbound.NotificationService;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Queue;

@Component
class TestNotificationService implements NotificationService {

    private final Queue<Notification> notifications = new ConcurrentLinkedQueue<>();

    @Override
    public void notifyOrderConfirmed(String orderId, String customerId) {
        notifications.add(new Notification(orderId, customerId));
    }

    public Queue<Notification> getNotifications() {
        return notifications;
    }

    public void clear() {
        notifications.clear();
    }

    record Notification(String orderId, String customerId) {}
}