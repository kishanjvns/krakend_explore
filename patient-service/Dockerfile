# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
# Cache dependency layer separately — only re-downloads when pom.xml changes
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -DskipTests -q

# ── Stage 2: Run ──────────────────────────────────────────────────────────────
# JRE-only alpine image — ~80 MB vs ~250 MB for full JDK
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# Source code does NOT end up in the final image — multi-stage build benefit
COPY --from=build /app/target/patient-service-1.0.0.jar patient-service.jar
EXPOSE 8081
# Container-aware JVM flags (Java 10+):
#   UseContainerSupport  : reads cgroup limits, not host memory
#   MaxRAMPercentage     : use 75% of container memory limit for heap
ENTRYPOINT ["java", "-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0", "-jar", "patient-service.jar"]
