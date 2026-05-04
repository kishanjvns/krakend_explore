package com.mediq.service;

import com.mediq.dto.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class UserCacheService {

    private static final Logger log = LoggerFactory.getLogger(UserCacheService.class);
    private static final String KEY_PREFIX = "user:v1:";

    private final RedisTemplate<String, UserResponse> redisTemplate;
    private final Duration ttl;

    public UserCacheService(
            RedisTemplate<String, UserResponse> redisTemplate,
            @Value("${mediq.cache.user.ttl-minutes}") int ttlMinutes) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public UserResponse get(UUID userId) {
        try {
            return redisTemplate.opsForValue().get(KEY_PREFIX + userId);
        } catch (Exception e) {
            log.warn("Redis read failed for userId={}: {}", userId, e.getMessage());
            return null; // fail open — fallback to DB
        }
    }

    public void put(UUID userId, UserResponse response) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + userId, response, ttl);
        } catch (Exception e) {
            log.warn("Redis write failed for userId={}: {}", userId, e.getMessage());
        }
    }

    public void evict(UUID userId) {
        try {
            redisTemplate.delete(KEY_PREFIX + userId);
        } catch (Exception e) {
            log.warn("Redis evict failed for userId={}: {}", userId, e.getMessage());
        }
    }
}
