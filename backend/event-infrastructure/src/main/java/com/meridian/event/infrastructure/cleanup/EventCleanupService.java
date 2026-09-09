package com.meridian.event.infrastructure.cleanup;

import com.meridian.event.infrastructure.persistence.jpa.OutboxEventEntity;
import com.meridian.event.infrastructure.persistence.jpa.ProcessedEventEntity;
import com.meridian.event.infrastructure.persistence.repository.OutboxEventRepository;
import com.meridian.event.infrastructure.persistence.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class EventCleanupService {

    private static final Logger log = LoggerFactory.getLogger(EventCleanupService.class);

    private final ProcessedEventRepository processedEventRepository;
    private final OutboxEventRepository outboxEventRepository;

    // Retention periods (configurable via env in production)
    private static final int PROCESSED_EVENTS_RETENTION_DAYS = 30;
    private static final int OUTBOX_EVENTS_RETENTION_DAYS = 7;
    private static final int BATCH_SIZE = 1000;

    public EventCleanupService(ProcessedEventRepository processedEventRepository,
                                OutboxEventRepository outboxEventRepository) {
        this.processedEventRepository = processedEventRepository;
        this.outboxEventRepository = outboxEventRepository;
    }

    @Scheduled(cron = "0 0 3 * * *") // Run daily at 3 AM
    @Transactional
    public void cleanupOldEvents() {
        Instant cutoffProcessed = Instant.now().minus(PROCESSED_EVENTS_RETENTION_DAYS, ChronoUnit.DAYS);
        Instant cutoffOutbox = Instant.now().minus(OUTBOX_EVENTS_RETENTION_DAYS, ChronoUnit.DAYS);

        int deletedProcessed = 0;
        int deletedOutbox = 0;

        // Clean up processed events in batches
        while (true) {
            var batch = processedEventRepository.findOldEvents(cutoffProcessed, PageRequest.of(0, BATCH_SIZE));
            if (batch.isEmpty()) break;
            processedEventRepository.deleteAll(batch);
            deletedProcessed += batch.size();
            if (batch.size() < BATCH_SIZE) break;
        }

        // Clean up sent outbox events in batches
        while (true) {
            var batch = outboxEventRepository.findOldSentEvents(cutoffOutbox, PageRequest.of(0, BATCH_SIZE));
            if (batch.isEmpty()) break;
            outboxEventRepository.deleteAll(batch);
            deletedOutbox += batch.size();
            if (batch.size() < BATCH_SIZE) break;
        }

        if (deletedProcessed > 0 || deletedOutbox > 0) {
            log.info("Cleanup completed: deleted {} processed events, {} outbox events",
                    deletedProcessed, deletedOutbox);
        }
    }
}