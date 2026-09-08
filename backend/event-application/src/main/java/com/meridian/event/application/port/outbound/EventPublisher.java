package com.meridian.event.application.port.outbound;

import com.meridian.event.domain.model.DomainEvent;

public interface EventPublisher {
    void publish(DomainEvent event);
}
