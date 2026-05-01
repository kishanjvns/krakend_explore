package com.trucare.interceptor;

import com.trucare.model.UserContext;

/**
 * ThreadLocal holder for UserContext.
 *
 * Java CONCEPT — ThreadLocal<T>
 *
 * ThreadLocal provides a variable that is unique to each thread.
 * Every thread that accesses a ThreadLocal gets its own independent copy.
 * No synchronisation is needed because threads never share the value.
 *
 * In a Tomcat web server:
 *   - Each HTTP request runs on a dedicated thread from the thread pool
 *   - ThreadLocal.set() in JwtClaimsInterceptor stores UserContext for that thread
 *   - ThreadLocal.get() anywhere in the request chain retrieves it for that thread only
 *   - ThreadLocal.remove() in afterCompletion cleans up before the thread returns to pool
 *
 * Java 8 vs Java 17:
 *   Java 8  : private static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();
 *   Java 17 : identical — ThreadLocal has not changed, but the pattern is now usually
 *             combined with records (the stored type) for clean immutability.
 *
 * Interview note:
 *   ThreadLocal is one of those Java fundamentals that appears in senior interviews.
 *   The classic mistake is forgetting to call .remove() — this causes memory leaks
 *   in thread-pool environments and cross-request data contamination.
 *   Spring's RequestContextHolder uses the same pattern internally.
 */
public final class UserContextHolder {

    private static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

    private UserContextHolder() {
        // utility class — no instantiation
    }

    public static void set(UserContext context) {
        HOLDER.set(context);
    }

    public static UserContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();   // .remove() is safer than .set(null) — avoids null checks
    }

    /**
     * Convenience method — returns true if a real (non-anonymous) user is present.
     */
    public static boolean isAuthenticated() {
        UserContext ctx = HOLDER.get();
        return ctx != null && !"anonymous".equals(ctx.userId());
    }
}
