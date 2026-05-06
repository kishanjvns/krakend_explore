# Stage 1 — build
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# Stage 2 — run
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/payment-service-1.0.0.jar payment-service.jar
EXPOSE 8089
ENTRYPOINT ["java", "-jar", "payment-service.jar"]
