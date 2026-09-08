package com.meridian.event.infrastructure.persistence.repository;

import com.meridian.event.infrastructure.persistence.jpa.OutboxEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, String> {

    @Query("SELECT e FROM OutboxEventEntity e WHERE e.sentAt IS NULL AND e.retryCount < 5 ORDER BY e.createdAt ASC")
    List<OutboxEventEntity> findUnsentEvents(Pageable pageable);

    long countBySentAtIsNull();
}