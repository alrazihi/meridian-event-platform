package com.meridian.event.infrastructure.persistence.repository;

import com.meridian.event.infrastructure.persistence.jpa.InventoryItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryItemJpaRepository extends JpaRepository<InventoryItemEntity, String> {
    Optional<InventoryItemEntity> findBySku(String sku);
}
