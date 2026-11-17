# Build from the repository root so Maven can include the frontend resources.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml mvnw ./
COPY .mvn .mvn
COPY backend/pom.xml backend/pom.xml
COPY backend/src backend/src
COPY frontend/templates frontend/templates
COPY frontend/static frontend/static
# CI runs verify (including H2 tests) before publishing this image.
RUN sh ./mvnw --batch-mode --no-transfer-progress -pl backend -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S -g 10001 cinema && adduser -S -D -H -u 10001 -G cinema cinema
COPY --from=build --chown=10001:10001 /workspace/backend/target/cinema-backend-*.jar /app/cinema.jar
USER 10001:10001
ENV SPRING_PROFILES_ACTIVE=production
EXPOSE 8080
# The management port is reachable only from inside this container.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=4 \
  CMD wget -q -O /dev/null http://127.0.0.1:8081/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/cinema.jar"]
