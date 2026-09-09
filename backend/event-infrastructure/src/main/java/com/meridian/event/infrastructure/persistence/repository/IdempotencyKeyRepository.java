package com.meridian.event.infrastructure.persistence.repository;

import com.meridian.event.infrastructure.persistence.jpa.IdempotencyKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyEntity, String> {
    Optional<IdempotencyKeyEntity> findByKeyHashAndEndpointAndExpiresAtAfter(String keyHash, String endpoint, Instant now);

    @Modifying
    @Query("DELETE FROM IdempotencyKeyEntity e WHERE e.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
