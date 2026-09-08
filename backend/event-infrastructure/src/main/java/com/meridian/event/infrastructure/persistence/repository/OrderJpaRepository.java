package com.meridian.event.infrastructure.persistence.repository;

import com.meridian.event.infrastructure.persistence.jpa.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderJpaRepository extends JpaRepository<OrderEntity, String> {
    Optional<OrderEntity> findById(String id);
}
