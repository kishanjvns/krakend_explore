package com.mediq.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private static final String BLACKLIST_KEY_PREFIX = "token:blacklist:";

    private final RedisTemplate<String, String> redisTemplate;

    public AuthController(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> me(
            @RequestHeader("X-User-Id")    String userId,
            @RequestHeader("X-User-Email") String email,
            @RequestHeader("X-User-Role")  String role,
            @RequestHeader(value = "X-User-Type",        required = false) String userType,
            @RequestHeader(value = "X-User-Permissions", required = false) String permissionsHeader) {

        log.debug("GET /auth/me userId={} role={}", userId, role);
        List<String> permissions = permissionsHeader != null
            ? List.of(permissionsHeader.split(","))
            : List.of();

        return ResponseEntity.ok(Map.of(
            "userId",      userId,
            "email",       email,
            "role",        role,
            "userType",    userType != null ? userType : role,
            "permissions", permissions
        ));
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> logout(
            @RequestHeader("X-Token-Jti") String jti,
            @RequestHeader("X-Token-Exp") String expEpochSeconds) {

        log.info("POST /auth/logout jti={}", jti);
        try {
            long expSeconds     = Long.parseLong(expEpochSeconds);
            long nowSeconds     = Instant.now().getEpochSecond();
            long remainingTtl   = expSeconds - nowSeconds;

            if (remainingTtl > 0) {
                redisTemplate.opsForValue().set(
                    BLACKLIST_KEY_PREFIX + jti,
                    "revoked",
                    Duration.ofSeconds(remainingTtl)
                );
                log.info("Token jti={} added to blacklist ttl={}s", jti, remainingTtl);
            } else {
                log.debug("Token jti={} already expired (remainingTtl={}s), skipping blacklist", jti, remainingTtl);
            }
        } catch (NumberFormatException e) {
            log.warn("Could not parse X-Token-Exp='{}' for jti={}: {}", expEpochSeconds, jti, e.getMessage());
        }

        return ResponseEntity.ok().build();
    }
}
