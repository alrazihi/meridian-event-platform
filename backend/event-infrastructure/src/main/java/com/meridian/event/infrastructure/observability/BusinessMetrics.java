package com.meridian.event.infrastructure.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class BusinessMetrics {

    private final MeterRegistry meterRegistry;
    
    // Counters
    private Counter ordersPlaced;
    private Counter ordersConfirmed;
    private Counter paymentsInitiated;
    private Counter paymentsApproved;
    private Counter paymentsRejected;
    private Counter inventoryReservations;
    private Counter inventoryReservationFailures;
    private Counter eventsPublished;
    private Counter eventsProcessed;
    private Counter eventsFailed;
    private Counter eventsDlq;
    private Counter outboxEventsPublished;
    private Counter outboxEventsFailed;
    private Counter outboxEventsDlq;
    
    // Gauges
    private AtomicLong pendingOutboxEvents = new AtomicLong(0);
    private AtomicLong pendingPayments = new AtomicLong(0);
    private AtomicLong inventoryItemsLow = new AtomicLong(0);

    public BusinessMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void init() {
        // Order metrics
        ordersPlaced = Counter.builder("meridian.orders.placed")
                .description("Total orders placed")
                .register(meterRegistry);
        
        ordersConfirmed = Counter.builder("meridian.orders.confirmed")
                .description("Total orders confirmed via event processing")
                .register(meterRegistry);
        
        // Payment metrics
        paymentsInitiated = Counter.builder("meridian.payments.initiated")
                .description("Total payments initiated (PENDING)")
                .register(meterRegistry);
        
        paymentsApproved = Counter.builder("meridian.payments.approved")
                .description("Total payments approved")
                .register(meterRegistry);
        
        paymentsRejected = Counter.builder("meridian.payments.rejected")
                .description("Total payments rejected")
                .register(meterRegistry);
        
        // Inventory metrics
        inventoryReservations = Counter.builder("meridian.inventory.reservations")
                .description("Total inventory reservations")
                .register(meterRegistry);
        
        inventoryReservationFailures = Counter.builder("meridian.inventory.reservation_failures")
                .description("Total inventory reservation failures")
                .register(meterRegistry);
        
        // Event metrics
        eventsPublished = Counter.builder("meridian.events.published")
                .description("Total domain events published to outbox")
                .register(meterRegistry);
        
        eventsProcessed = Counter.builder("meridian.events.processed")
                .description("Total events successfully processed by consumers")
                .register(meterRegistry);
        
        eventsFailed = Counter.builder("meridian.events.failed")
                .description("Total events failed after max retries")
                .register(meterRegistry);
        
        eventsDlq = Counter.builder("meridian.events.dlq")
                .description("Total events sent to DLQ")
                .register(meterRegistry);
        
        // Outbox metrics
        outboxEventsPublished = Counter.builder("meridian.outbox.published")
                .description("Total outbox events published to Kafka")
                .register(meterRegistry);
        
        outboxEventsFailed = Counter.builder("meridian.outbox.failed")
                .description("Total outbox events failed (retryable)")
                .register(meterRegistry);
        
        outboxEventsDlq = Counter.builder("meridian.outbox.dlq")
                .description("Total outbox events sent to DLQ")
                .register(meterRegistry);

        // Gauges
        Gauge.builder("meridian.outbox.pending", pendingOutboxEvents, AtomicLong::get)
                .description("Number of pending outbox events")
                .register(meterRegistry);
        
        Gauge.builder("meridian.payments.pending", pendingPayments, AtomicLong::get)
                .description("Number of payments in PENDING state")
                .register(meterRegistry);
        
        Gauge.builder("meridian.inventory.low_stock", inventoryItemsLow, AtomicLong::get)
                .description("Number of inventory items below low stock threshold")
                .register(meterRegistry);
    }

    // Order metrics
    public void incrementOrdersPlaced() { ordersPlaced.increment(); }
    public void incrementOrdersConfirmed() { ordersConfirmed.increment(); }

    // Payment metrics
    public void incrementPaymentsInitiated() { paymentsInitiated.increment(); }
    public void incrementPaymentsApproved() { paymentsApproved.increment(); }
    public void incrementPaymentsRejected() { paymentsRejected.increment(); }

    // Inventory metrics
    public void incrementInventoryReservations() { inventoryReservations.increment(); }
    public void incrementInventoryReservationFailures() { inventoryReservationFailures.increment(); }

    // Event metrics
    public void incrementEventsPublished() { eventsPublished.increment(); }
    public void incrementEventsProcessed() { eventsProcessed.increment(); }
    public void incrementEventsFailed() { eventsFailed.increment(); }
    public void incrementEventsDlq() { eventsDlq.increment(); }

    // Outbox metrics
    public void incrementOutboxEventsPublished() { outboxEventsPublished.increment(); }
    public void incrementOutboxEventsFailed() { outboxEventsFailed.increment(); }
    public void incrementOutboxEventsDlq() { outboxEventsDlq.increment(); }

    // Gauge setters
    public void setPendingOutboxEvents(long count) { pendingOutboxEvents.set(count); }
    public void setPendingPayments(long count) { pendingPayments.set(count); }
    public void setInventoryItemsLow(long count) { inventoryItemsLow.set(count); }
}