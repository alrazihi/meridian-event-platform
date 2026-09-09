package com.meridian.event.infrastructure.concurrency;

import com.meridian.event.application.port.outbound.InventoryItemRepository;
import com.meridian.event.domain.model.InventoryItem;
import com.meridian.event.domain.model.valueobjects.Sku;
import com.meridian.event.infrastructure.config.TestConfig;
import com.meridian.event.infrastructure.persistence.adapter.RepositoryTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Import({RepositoryTestConfig.class, TestConfig.class})
@DirtiesContext
@Transactional
class InventoryConcurrencyTest {

    @Autowired
    private InventoryItemRepository inventoryRepository;

    private static final String SKU = "CONCURRENCY-SKU-001";
    private static final int INITIAL_STOCK = 100;

    @BeforeEach
    void setUp() {
        InventoryItem item = new InventoryItem(Sku.of(SKU), INITIAL_STOCK);
        inventoryRepository.save(item);
    }

    @Test
    void shouldPreventOversellingUnderConcurrentReservations() throws InterruptedException {
        int threadCount = 20;
        int quantityPerThread = 10;
        int totalRequested = threadCount * quantityPerThread;
        
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    InventoryItem item = inventoryRepository.findById(Sku.of(SKU))
                            .orElseThrow(() -> new IllegalStateException("Inventory item not found"));
                    
                    if (item.getAvailableQuantity() >= quantityPerThread) {
                        item.reserve(quantityPerThread);
                        inventoryRepository.save(item);
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertThat(endLatch.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        InventoryItem finalItem = inventoryRepository.findById(Sku.of(SKU))
                .orElseThrow(() -> new IllegalStateException("Inventory item not found"));

        int totalReserved = successCount.get() * quantityPerThread;
        assertThat(finalItem.getAvailableQuantity()).isEqualTo(INITIAL_STOCK - totalReserved);
        assertThat(finalItem.getReservedQuantity()).isEqualTo(totalReserved);
        assertThat(finalItem.getAvailableQuantity()).isGreaterThanOrEqualTo(0);
        assertThat(totalReserved).isLessThanOrEqualTo(INITIAL_STOCK);
    }

    @Test
    void shouldHandleConcurrentReserveAndRelease() throws InterruptedException {
        int reserveThreads = 10;
        int releaseThreads = 10;
        int quantityPerThread = 5;
        
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(reserveThreads + releaseThreads);
        ExecutorService executor = Executors.newFixedThreadPool(reserveThreads + releaseThreads);
        AtomicInteger reserveSuccess = new AtomicInteger(0);
        AtomicInteger releaseSuccess = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < reserveThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    InventoryItem item = inventoryRepository.findById(Sku.of(SKU))
                            .orElseThrow(() -> new IllegalStateException("Inventory item not found"));
                    if (item.getAvailableQuantity() >= quantityPerThread) {
                        item.reserve(quantityPerThread);
                        inventoryRepository.save(item);
                        reserveSuccess.incrementAndGet();
                    } else {
                        failures.incrementAndGet();
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        for (int i = 0; i < releaseThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    InventoryItem item = inventoryRepository.findById(Sku.of(SKU))
                            .orElseThrow(() -> new IllegalStateException("Inventory item not found"));
                    if (item.getReservedQuantity() >= quantityPerThread) {
                        item.release(quantityPerThread);
                        inventoryRepository.save(item);
                        releaseSuccess.incrementAndGet();
                    } else {
                        failures.incrementAndGet();
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertThat(endLatch.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        InventoryItem finalItem = inventoryRepository.findById(Sku.of(SKU))
                .orElseThrow(() -> new IllegalStateException("Inventory item not found"));

        int netReserved = (reserveSuccess.get() - releaseSuccess.get()) * quantityPerThread;
        assertThat(finalItem.getAvailableQuantity() + finalItem.getReservedQuantity()).isEqualTo(INITIAL_STOCK);
        assertThat(finalItem.getAvailableQuantity()).isGreaterThanOrEqualTo(0);
        assertThat(finalItem.getReservedQuantity()).isGreaterThanOrEqualTo(0);
    }
}
