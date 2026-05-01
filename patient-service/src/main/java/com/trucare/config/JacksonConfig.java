package com.resources.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot CONCEPT — @Configuration + @Bean
 *
 * @Configuration   : marks this class as a source of bean definitions.
 * @Bean            : the return value of this method is a Spring-managed bean.
 *
 * Why we need this:
 *   Spring Boot auto-configures Jackson by default, but without JavaTimeModule
 *   it cannot serialise java.time types (Instant, LocalDate, etc.) correctly.
 *   By default, Instant serialises as a numeric array [seconds, nanos].
 *   With JavaTimeModule + WRITE_DATES_AS_TIMESTAMPS=false, it becomes
 *   an ISO 8601 string: "2024-04-01T10:30:00Z" — human-readable.
 *
 * Spring Boot auto-configuration back-off:
 *   When Spring Boot sees a user-defined ObjectMapper @Bean, it backs off
 *   its own auto-configured ObjectMapper. This is the "conditional on missing
 *   bean" (@ConditionalOnMissingBean) pattern — the cornerstone of how
 *   Spring Boot auto-configuration works.
 *
 * Interview note:
 *   Understanding @ConditionalOnMissingBean is key to understanding Spring Boot
 *   auto-configuration. It's why "convention over configuration" works —
 *   defaults apply until you provide your own definition.
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
