# Multi-stage Dockerfile for Meridian Event Platform
# Stage 1: Build
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /app

# Install Maven
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# Copy pom files first for layer caching
COPY backend/pom.xml backend/event-domain/pom.xml backend/event-application/pom.xml backend/event-infrastructure/pom.xml backend/event-service/pom.xml ./
COPY backend/parent-pom.xml ./backend/

# Download dependencies
RUN mvn dependency:go-offline -pl event-domain,event-application,event-infrastructure,event-service -am -q

# Copy source code
COPY backend/event-domain/src ./event-domain/src
COPY backend/event-application/src ./event-application/src
COPY backend/event-infrastructure/src ./event-infrastructure/src
COPY backend/event-service/src ./event-service/src

# Build application
RUN mvn clean package -pl event-domain,event-application,event-infrastructure,event-service -am \
    -DskipTests \
    -Dmaven.compiler.failOnError=true \
    -q

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

# Create non-root user
RUN groupadd -r meridian && useradd -r -g meridian -d /app -s /sbin/nologin meridian

# Copy JAR from builder
COPY --from=builder --chown=meridian:meridian /app/event-service/target/event-service-*.jar app.jar

# Create directories for logs and temp
RUN mkdir -p /var/log/meridian /tmp/meridian && \
    chown -R meridian:meridian /var/log/meridian /tmp/meridian

# Switch to non-root user
USER meridian

# Expose port
EXPOSE 8080

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -XX:+UseStringDeduplication \
    -XX:+UnlockExperimentalVMOptions \
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/var/log/meridian/heapdump.hprof \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.profiles.active=prod"

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health/liveness || exit 1

# Run application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
