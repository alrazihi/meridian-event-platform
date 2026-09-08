package com.meridian.event.infrastructure.persistence.repository;

import com.meridian.event.infrastructure.persistence.jpa.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, String> {
    Optional<PaymentEntity> findById(String id);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM PaymentEntity p WHERE p.orderId = ?1 AND p.status = ?2")
    boolean existsByOrderIdAndStatus(String orderId, String status);
}
