package com.meridian.event.infrastructure.persistence.repository;

import com.meridian.event.infrastructure.persistence.jpa.OutboxEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.LockModeType;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM OutboxEventEntity e WHERE e.sentAt IS NULL AND e.retryCount < 5 ORDER BY e.createdAt ASC")
    List<OutboxEventEntity> findUnsentEvents(Pageable pageable);

    @Query("SELECT e FROM OutboxEventEntity e WHERE e.sentAt IS NOT NULL AND e.sentAt < :cutoff")
    List<OutboxEventEntity> findOldSentEvents(Instant cutoff, Pageable pageable);

    long countBySentAtIsNull();

    @Query("SELECT count(e) FROM OutboxEventEntity e WHERE e.sentAt IS NULL AND e.createdAt < :cutoff")
    long countOldUnsentEvents(Instant cutoff);

    @Query("SELECT count(e) FROM OutboxEventEntity e WHERE e.sentAt IS NULL AND e.retryCount > 0")
    long countRetryingEvents();
}