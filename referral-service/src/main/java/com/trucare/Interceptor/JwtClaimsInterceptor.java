package com.trucare.interceptor;

import com.trucare.model.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Spring Boot CONCEPT — HandlerInterceptor
 *
 * Runs around every HTTP request that Spring MVC handles.
 * Three hook points:
 *   preHandle()       → runs BEFORE the controller method
 *   postHandle()      → runs AFTER controller, BEFORE response written
 *   afterCompletion() → runs AFTER response written — used for cleanup
 *
 * This interceptor:
 *   1. Reads KrakenD-forwarded JWT claim headers from every incoming request
 *   2. Builds a UserContext record from those headers
 *   3. Stores UserContext in ThreadLocal via UserContextHolder
 *   4. Cleans up the ThreadLocal after the request (afterCompletion)
 */
@Component
public class JwtClaimsInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtClaimsInterceptor.class);

    public static final String HEADER_USER_ID    = "X-User-Id";
    public static final String HEADER_USER_EMAIL = "X-User-Email";
    public static final String HEADER_USER_ROLE  = "X-User-Role";
    public static final String HEADER_USER_NAME  = "X-User-Name";

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {

        String userId = request.getHeader(HEADER_USER_ID);
        String email  = request.getHeader(HEADER_USER_EMAIL);
        String role   = request.getHeader(HEADER_USER_ROLE);
        String name   = request.getHeader(HEADER_USER_NAME);

        if (userId != null && !userId.isBlank()) {
            UserContext ctx = new UserContext(userId, email, role, name);
            UserContextHolder.set(ctx);
            log.debug("UserContext set: userId={}, role={}", userId, role);
        } else {
            UserContextHolder.set(UserContext.anonymous());
            log.debug("No X-User-Id header — using anonymous context");
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        UserContextHolder.clear();
        log.debug("UserContext cleared after request completion");
    }
}
