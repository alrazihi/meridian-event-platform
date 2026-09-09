package com.meridian.event.application.service;

import com.meridian.event.application.port.inbound.ReserveInventoryUseCase;
import com.meridian.event.application.port.outbound.EventPublisher;
import com.meridian.event.application.port.outbound.InventoryItemRepository;
import com.meridian.event.domain.model.InventoryItem;
import com.meridian.event.domain.model.valueobjects.Sku;
import com.meridian.event.domain.model.InventoryReservedEvent;
import com.meridian.event.infrastructure.observability.BusinessMetrics;
import com.meridian.event.infrastructure.observability.CorrelationIdContext;
import com.meridian.event.infrastructure.security.audit.SecurityAuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

@Service
public class DefaultInventoryService implements ReserveInventoryUseCase {

    private static final Logger log = LoggerFactory.getLogger("WORKFLOW");

    private final InventoryItemRepository inventoryRepository;
    private final EventPublisher eventPublisher;
    private final SecurityAuditLogger auditLogger;
    private final HttpServletRequest request;
    private final BusinessMetrics businessMetrics;

    public DefaultInventoryService(
            InventoryItemRepository inventoryRepository,
            EventPublisher eventPublisher,
            SecurityAuditLogger auditLogger,
            HttpServletRequest request,
            BusinessMetrics businessMetrics) {
        this.inventoryRepository = inventoryRepository;
        this.eventPublisher = eventPublisher;
        this.auditLogger = auditLogger;
        this.request = request;
        this.businessMetrics = businessMetrics;
    }

    @Override
    @Transactional
    public InventoryItem reserveInventory(String sku, int quantity, String tenantId) {
        String correlationId = CorrelationIdContext.getCorrelationId();
        String ip = getClientIp();
        Sku skuObj = Sku.of(sku);

        log.info("Inventory reservation started sku={} quantity={} tenantId={} correlationId={}",
                sku, quantity, tenantId, correlationId);

        Optional<InventoryItem> existingItem = inventoryRepository.findById(skuObj);
        if (existingItem.isEmpty()) {
            auditLogger.logInventoryReservation(tenantId, ip, sku, quantity, "NOT_FOUND");
            log.warn("Inventory item not found sku={} tenantId={} correlationId={}", sku, tenantId, correlationId);
            throw new IllegalStateException("Inventory item not found for SKU: " + sku);
        }

        InventoryItem item = existingItem.get();

        if (tenantId == null || tenantId.trim().isEmpty()) {
            auditLogger.logInventoryReservation(tenantId, ip, sku, quantity, "INVALID_TENANT");
            log.warn("Invalid tenant ID sku={} tenantId={} correlationId={}", sku, tenantId, correlationId);
            throw new IllegalArgumentException("Tenant ID is required");
        }

        try {
            item.reserve(quantity);

            InventoryItem savedItem = inventoryRepository.save(item);

            InventoryReservedEvent event = new InventoryReservedEvent(skuObj, quantity, sku);
            eventPublisher.publish(event);

            auditLogger.logInventoryReservation(tenantId, ip, sku, quantity, "SUCCESS");
            log.info("Inventory reserved sku={} quantity={} available={} reserved={} tenantId={} correlationId={}",
                    sku, quantity, savedItem.getAvailableQuantity(), savedItem.getReservedQuantity(), tenantId, correlationId);

            businessMetrics.incrementInventoryReservations();
            businessMetrics.incrementEventsPublished();

            return savedItem;
        } catch (IllegalStateException e) {
            auditLogger.logInventoryReservation(tenantId, ip, sku, quantity, "INSUFFICIENT_STOCK");
            log.warn("Insufficient stock sku={} requested={} available={} tenantId={} correlationId={}",
                    sku, quantity, item.getAvailableQuantity(), tenantId, correlationId);

            businessMetrics.incrementInventoryReservationFailures();
            throw e;
        }
    }

    private String getClientIp() {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}



