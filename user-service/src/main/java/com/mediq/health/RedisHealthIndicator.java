package com.mediq.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component("redis")
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory connectionFactory;

    public RedisHealthIndicator(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Health health() {
        try {
            String pong = connectionFactory.getConnection().ping();
            if ("PONG".equals(pong)) {
                return Health.up()
                    .withDetail("redis", "reachable")
                    .withDetail("response", pong)
                    .build();
            }
            return Health.down()
                .withDetail("error", "Unexpected ping response: " + pong)
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("redis", "unreachable")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
