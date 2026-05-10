package com.mediq.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final KrakenDAuthFilter krakenDAuthFilter;

    public SecurityConfig(KrakenDAuthFilter krakenDAuthFilter) {
        this.krakenDAuthFilter = krakenDAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers(
                    "/users/patients/register",
                    "/users/doctors/register",
                    "/users/{userId}/send-otp",
                    "/users/{userId}/verify-otp",
                    "/actuator/**"
                ).permitAll()
                .anyRequest().authenticated()
            )
            // Add our custom filter before the standard auth filter
            .addFilterBefore(krakenDAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
