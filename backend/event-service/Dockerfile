FROM eclipse-temurin:21-jre-alpine
VOLUME /tmp
COPY target/event-service-1.0.0-SNAPSHOT.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
