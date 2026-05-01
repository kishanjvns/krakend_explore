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
 * An interceptor runs around every HTTP request that Spring MVC handles.
 * It provides three hook points:
 *
 *   preHandle()   → runs BEFORE the controller method
 *                   return false to abort the request (e.g. block unauthorised)
 *                   return true to continue to the controller
 *
 *   postHandle()  → runs AFTER the controller method but BEFORE the response
 *                   is written — can modify the ModelAndView
 *
 *   afterCompletion() → runs AFTER the response is written
 *                       used for cleanup, logging, releasing resources
 *
 * This interceptor:
 *   1. Reads KrakenD-forwarded JWT claim headers from every incoming request
 *   2. Builds a UserContext record from those headers
 *   3. Stores UserContext in a ThreadLocal via UserContextHolder
 *   4. Cleans up the ThreadLocal after the request (afterCompletion)
 *
 * Interview note:
 *   ThreadLocal is used here because each HTTP request runs on its own thread
 *   in Tomcat's thread pool. ThreadLocal gives each thread its own isolated
 *   copy of UserContext without synchronisation overhead.
 *   ALWAYS clear ThreadLocal in afterCompletion — Tomcat reuses threads,
 *   so a leaked ThreadLocal value from Request 1 would pollute Request 2.
 */
@Component
public class JwtClaimsInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtClaimsInterceptor.class);

    /**
     * KrakenD header names — these must exactly match what is configured
     * in krakend.json under "headers_to_pass" or "jwt_validation".
     * Change here if you change the KrakenD propagation config.
     */
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
            /*
             * No X-User-Id header means either:
             *   a) This is a public endpoint (no JWT required in krakend.json)
             *   b) A misconfigured KrakenD endpoint forgot headers_to_pass
             *
             * We set GUEST context so controllers can still distinguish
             * authenticated vs unauthenticated access.
             */
            UserContextHolder.set(UserContext.anonymous());
            log.debug("No X-User-Id header — using anonymous context");
        }

        return true; // always continue — auth enforcement is at KrakenD level
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        /*
         * CRITICAL: always clear ThreadLocal after each request.
         * Tomcat reuses threads — not clearing causes user A's context
         * to bleed into user B's subsequent request on the same thread.
         */
        UserContextHolder.clear();
        log.debug("UserContext cleared after request completion");
    }
}
