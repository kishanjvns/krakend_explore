package com.trucare.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson configuration — same pattern as patient-service.
 * Registers JavaTimeModule so Instant in ErrorResponse serialises
 * as ISO 8601 string ("2024-04-01T10:30:00Z") not a numeric array.
 *
 * Spring Boot CONCEPT — Auto-configuration back-off:
 *   When Spring Boot sees a user-defined ObjectMapper @Bean,
 *   it backs off its own auto-configured one (@ConditionalOnMissingBean).
 *   This is core to understanding how Spring Boot customisation works.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
