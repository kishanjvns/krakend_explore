# Stage 1 — extract the keycloak-model-storage JAR (not on Maven Central)
FROM quay.io/keycloak/keycloak:24.0.3 AS kc-base

# Stage 2 — build the SPI thin JAR with Maven
FROM maven:3.9-eclipse-temurin-17 AS spi-builder

COPY --from=kc-base /opt/keycloak/lib/lib/main/org.keycloak.keycloak-model-storage-24.0.3.jar \
     /tmp/keycloak-model-storage.jar
RUN mvn install:install-file \
        -Dfile=/tmp/keycloak-model-storage.jar \
        -DgroupId=org.keycloak \
        -DartifactId=keycloak-model-storage \
        -Dversion=24.0.3 \
        -Dpackaging=jar -q

WORKDIR /spi
COPY pom.xml .
RUN mvn dependency:go-offline -q 2>/dev/null || true
COPY src ./src
RUN mvn clean package -DskipTests -q

# Stage 3 — Keycloak image with SPI and its runtime deps baked in
FROM quay.io/keycloak/keycloak:24.0.3
COPY --from=spi-builder /spi/target/keycloak-user-spi-1.0.0.jar /opt/keycloak/providers/
COPY --from=spi-builder /root/.m2/repository/org/mindrot/jbcrypt/0.4/jbcrypt-0.4.jar \
     /opt/keycloak/providers/
COPY --from=spi-builder /root/.m2/repository/com/zaxxer/HikariCP/5.1.0/HikariCP-5.1.0.jar \
     /opt/keycloak/providers/
COPY --from=spi-builder /root/.m2/repository/org/postgresql/postgresql/42.7.3/postgresql-42.7.3.jar \
     /opt/keycloak/providers/
# Also make keycloak-model-storage explicitly available in providers/ classloader
# (AbstractUserAdapter lives here; providers classloader may not see lib/lib/main)
COPY --from=kc-base /opt/keycloak/lib/lib/main/org.keycloak.keycloak-model-storage-24.0.3.jar \
     /opt/keycloak/providers/
RUN /opt/keycloak/bin/kc.sh build
