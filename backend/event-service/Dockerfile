FROM eclipse-temurin:21-jre-alpine

# Create non-root user
RUN addgroup -g 1000 -S appgroup && \
    adduser -u 1000 -S appuser -G appgroup

VOLUME /tmp
COPY target/event-service-1.0.0-SNAPSHOT.jar app.jar

# Change ownership to non-root user
RUN chown appuser:appgroup /app.jar

USER appuser

ENTRYPOINT ["java","-jar","/app.jar"]
