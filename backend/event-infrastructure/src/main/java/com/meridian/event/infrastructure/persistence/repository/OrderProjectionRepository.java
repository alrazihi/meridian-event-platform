package com.meridian.event.infrastructure.persistence.repository;

import com.meridian.event.infrastructure.persistence.jpa.OrderProjectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderProjectionRepository extends JpaRepository<OrderProjectionEntity, String> {
    Optional<OrderProjectionEntity> findByOrderId(String orderId);
}