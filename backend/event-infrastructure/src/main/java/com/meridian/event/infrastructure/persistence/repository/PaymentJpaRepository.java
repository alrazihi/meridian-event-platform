package com.meridian.event.infrastructure.persistence.repository;

import com.meridian.event.infrastructure.persistence.jpa.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, String> {
    Optional<PaymentEntity> findById(String id);
}
