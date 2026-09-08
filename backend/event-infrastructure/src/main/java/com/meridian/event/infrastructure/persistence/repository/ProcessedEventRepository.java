package com.meridian.event.infrastructure.persistence.repository;

import com.meridian.event.infrastructure.persistence.jpa.ProcessedEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEventEntity, String> {
    Optional<ProcessedEventEntity> findByEventId(String eventId);
    boolean existsByEventId(String eventId);
    boolean existsByEventIdAndCustomerId(String eventId, String customerId);

    @Query("SELECT e FROM ProcessedEventEntity e WHERE e.processedAt < :cutoff")
    List<ProcessedEventEntity> findOldEvents(Instant cutoff, Pageable pageable);
}