package com.mediq.controller;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String BLACKLIST_KEY_PREFIX = "token:blacklist:";

    private final RedisTemplate<String, String> redisTemplate;

    public AuthController(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> me(
            @RequestHeader("X-User-Id")          String userId,
            @RequestHeader("X-Keycloak-Id")       String keycloakId,
            @RequestHeader("X-User-Email")        String email,
            @RequestHeader("X-User-Role")         String role,
            @RequestHeader("X-User-Type")         String userType,
            @RequestHeader(value = "X-User-Permissions", required = false) String permissionsHeader) {

        List<String> permissions = permissionsHeader != null
            ? List.of(permissionsHeader.split(","))
            : List.of();

        return ResponseEntity.ok(Map.of(
            "userId",      userId,
            "keycloakId",  keycloakId,
            "email",       email,
            "role",        role,
            "userType",    userType,
            "permissions", permissions
        ));
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> logout(
            @RequestHeader("X-Token-Jti") String jti,
            @RequestHeader("X-Token-Exp") String expEpochSeconds) {

        try {
            long expSeconds = Long.parseLong(expEpochSeconds);
            long nowSeconds = Instant.now().getEpochSecond();
            long remainingTtl = expSeconds - nowSeconds;

            if (remainingTtl > 0) {
                redisTemplate.opsForValue().set(
                    BLACKLIST_KEY_PREFIX + jti,
                    "revoked",
                    Duration.ofSeconds(remainingTtl)
                );
            }
        } catch (NumberFormatException ignored) {
        }

        return ResponseEntity.ok().build();
    }
}
