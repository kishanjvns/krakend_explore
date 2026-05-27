# ── Stage 1: Build App ────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -DskipTests -q

# ── Stage 2: Analyze & Build Custom JRE ───────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS jre-build
WORKDIR /jre-setup

# Copy the built jar from Stage 1
COPY --from=build /app/target/payment-service-1.0.0.jar app.jar

# Unpack the fat JAR because jdeps needs to scan the internal dependencies
RUN unzip -q app.jar -d unpacked

# Run jdeps to automatically find all required modules
RUN $JAVA_HOME/bin/jdeps \
    --ignore-missing-deps \
    --print-module-deps \
    --recursive \
    --multi-release 17 \
    --class-path "unpacked/BOOT-INF/lib/*" \
    app.jar > modules.txt

# Build the custom JRE using the exact modules discovered by jdeps
RUN $JAVA_HOME/bin/jlink \
         --add-modules $(cat modules.txt),jdk.unsupported,java.instrument \
         --strip-debug \
         --no-man-pages \
         --no-header-files \
         --compress=2 \
         --output /custom-jre

# ── Stage 3: Run ──────────────────────────────────────────────────────────────
FROM alpine:3.19
WORKDIR /app

RUN apk add --no-cache gcompat libstdc++

COPY --from=jre-build /custom-jre /jre
COPY --from=build /app/target/payment-service-1.0.0.jar payment-service.jar

ENV PATH="/jre/bin:$PATH"
EXPOSE 8089
ENTRYPOINT ["java","-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "payment-service.jar"]
