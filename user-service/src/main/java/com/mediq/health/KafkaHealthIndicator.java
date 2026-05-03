package com.mediq.health;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component("kafka")
public class KafkaHealthIndicator implements HealthIndicator {

    private final String bootstrapServers;

    public KafkaHealthIndicator(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    @Override
    public Health health() {
        try (AdminClient client = AdminClient.create(
                Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers))) {
            client.listTopics().names().get(3, TimeUnit.SECONDS);
            return Health.up()
                .withDetail("kafka", "reachable")
                .withDetail("brokers", bootstrapServers)
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("kafka", "unreachable")
                .withDetail("brokers", bootstrapServers)
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
