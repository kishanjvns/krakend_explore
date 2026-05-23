package com.mediq.emr.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class KrakenDAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(KrakenDAuthFilter.class);

    private static final String HEADER_USER_ID          = "X-User-Id";
    private static final String HEADER_USER_ROLE        = "X-User-Role";
    private static final String HEADER_USER_PERMISSIONS = "X-User-Permissions";
    private static final String HEADER_TOKEN_JTI        = "X-Token-Jti";
    private static final String BLACKLIST_KEY_PREFIX    = "token:blacklist:";

    private final RedisTemplate<String, String> redisTemplate;

    public KrakenDAuthFilter(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String userId = request.getHeader(HEADER_USER_ID);
        String role   = request.getHeader(HEADER_USER_ROLE);
        String jti    = request.getHeader(HEADER_TOKEN_JTI);

        log.debug("KrakenDAuthFilter → {} {} | userId={} role={} jti={}",
            request.getMethod(), request.getRequestURI(), userId, role, jti);

        if (userId != null && !userId.isBlank()) {
            if (jti != null && !jti.isBlank()) {
                try {
                    Boolean revoked = redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + jti);
                    log.debug("Redis blacklist check jti={} revoked={}", jti, revoked);
                    if (Boolean.TRUE.equals(revoked)) {
                        log.warn("Rejected revoked token jti={} userId={}", jti, userId);
                        response.setStatus(HttpStatus.UNAUTHORIZED.value());
                        response.getWriter().write("Token has been revoked");
                        return;
                    }
                } catch (Exception e) {
                    log.error("Redis blacklist check failed for jti={} — failing open: {}", jti, e.getMessage());
                }
            }

            List<GrantedAuthority> authorities = new ArrayList<>();
            if (role != null && !role.isBlank()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
            }
            String permissionsHeader = request.getHeader(HEADER_USER_PERMISSIONS);
            if (permissionsHeader != null && !permissionsHeader.isBlank()) {
                String cleaned = permissionsHeader.trim();
                if (cleaned.startsWith("[")) {
                    cleaned = cleaned.replaceAll("[\\[\\]\"]", "");
                }
                for (String permission : cleaned.split("[,\\s]+")) {
                    if (!permission.trim().isBlank()) {
                        authorities.add(new SimpleGrantedAuthority(permission.trim()));
                    }
                }
            }

            log.debug("Setting security context userId={} authorities={}", userId, authorities);
            SecurityContextHolder.getContext().setAuthentication(
                new PreAuthenticatedAuthenticationToken(userId, null, authorities));
        } else {
            log.debug("No X-User-Id header — anonymous request for {} {}", request.getMethod(), request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }
}
