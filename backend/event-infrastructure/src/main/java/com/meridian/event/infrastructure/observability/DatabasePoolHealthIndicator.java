package com.meridian.event.infrastructure.observability;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class DatabasePoolHealthIndicator implements HealthIndicator {

    private static final double MAX_USAGE_PERCENT = 0.85;

    private final HikariDataSource dataSource;

    public DatabasePoolHealthIndicator(DataSource dataSource) {
        this.dataSource = (HikariDataSource) dataSource;
    }

    @Override
    public Health health() {
        int totalConnections = dataSource.getMaximumPoolSize();
        int activeConnections = dataSource.getHikariPoolMXBean().getActiveConnections();
        int idleConnections = dataSource.getHikariPoolMXBean().getIdleConnections();
        int waitingThreads = dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection();
        
        double usagePercent = (double) activeConnections / totalConnections;

        Health.Builder builder = usagePercent < MAX_USAGE_PERCENT && waitingThreads == 0 ? Health.up() : Health.down();

        builder.withDetail("totalConnections", totalConnections)
                .withDetail("activeConnections", activeConnections)
                .withDetail("idleConnections", idleConnections)
                .withDetail("waitingThreads", waitingThreads)
                .withDetail("usagePercent", Math.round(usagePercent * 100.0) / 100.0)
                .withDetail("maxUsagePercentThreshold", MAX_USAGE_PERCENT);

        if (waitingThreads > 0) {
            builder.withDetail("warning", "Threads waiting for connection: " + waitingThreads);
        }
        if (usagePercent >= MAX_USAGE_PERCENT) {
            builder.withDetail("warning", "Connection pool usage above threshold: " + Math.round(usagePercent * 100) + "%");
        }

        return builder.build();
    }
}