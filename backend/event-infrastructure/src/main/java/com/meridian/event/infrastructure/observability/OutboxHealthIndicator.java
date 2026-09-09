package com.meridian.event.infrastructure.observability;

import com.meridian.event.infrastructure.persistence.repository.OutboxEventRepository;
import com.meridian.event.infrastructure.persistence.repository.ProcessedEventRepository;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class OutboxHealthIndicator implements HealthIndicator {

    private static final int MAX_PENDING_EVENTS = 1000;
    private static final int MAX_AGE_MINUTES = 5;

    private final OutboxEventRepository outboxRepository;

    public OutboxHealthIndicator(OutboxEventRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Override
    public Health health() {
        long pendingCount = outboxRepository.countBySentAtIsNull();
        long oldPendingCount = outboxRepository.countOldUnsentEvents(Instant.now().minus(MAX_AGE_MINUTES, ChronoUnit.MINUTES));
        long retryingCount = outboxRepository.countRetryingEvents();

        Health.Builder builder = pendingCount > MAX_PENDING_EVENTS ? Health.down() : Health.up();

        builder.withDetail("pendingEvents", pendingCount)
                .withDetail("oldPendingEvents", oldPendingCount)
                .withDetail("retryingEvents", retryingCount)
                .withDetail("threshold", MAX_PENDING_EVENTS);

        if (oldPendingCount > 0) {
            builder.withDetail("warning", "Events pending for more than " + MAX_AGE_MINUTES + " minutes");
        }

        return builder.build();
    }
}