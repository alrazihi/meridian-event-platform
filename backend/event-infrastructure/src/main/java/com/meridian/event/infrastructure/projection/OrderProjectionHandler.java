package com.meridian.event.infrastructure.projection;

import com.meridian.event.domain.model.DomainEvent;
import com.meridian.event.domain.model.OrderConfirmedEvent;
import com.meridian.event.infrastructure.persistence.jpa.OrderProjectionEntity;
import com.meridian.event.infrastructure.persistence.repository.OrderProjectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class OrderProjectionHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderProjectionHandler.class);

    private final OrderProjectionRepository projectionRepository;

    public OrderProjectionHandler(OrderProjectionRepository projectionRepository) {
        this.projectionRepository = projectionRepository;
    }

    @Transactional
    public void handle(DomainEvent event) {
        if (event instanceof OrderConfirmedEvent confirmedEvent) {
            handleOrderConfirmed(confirmedEvent);
        }
    }

    private void handleOrderConfirmed(OrderConfirmedEvent event) {
        OrderProjectionEntity projection = projectionRepository.findByOrderId(event.getAggregateId())
                .orElseGet(() -> {
                    OrderProjectionEntity newProjection = new OrderProjectionEntity();
                    newProjection.setOrderId(event.getAggregateId());
                    newProjection.setCustomerId(event.getCustomerId());
                    newProjection.setCreatedAt(event.getOccurredAt());
                    return newProjection;
                });

        projection.setCustomerId(event.getCustomerId());
        projection.setTotal(new BigDecimal(event.getTotalAmount()));
        projection.setStatus("CONFIRMED");
        projection.setVersion(projection.getVersion() + 1);
        projection.setUpdatedAt(Instant.now());

        projectionRepository.save(projection);
        log.debug("Updated order projection for order {}", event.getAggregateId());
    }
}