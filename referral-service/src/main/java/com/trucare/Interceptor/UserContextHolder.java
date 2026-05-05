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
 * CRITICAL: Always call .remove() after each request.
 * Tomcat reuses threads — not removing causes the previous user's context
 * to bleed into the next request on the same thread. Security bug + memory leak.
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
        HOLDER.remove();   // .remove() is safer than .set(null)
    }

    public static boolean isAuthenticated() {
        UserContext ctx = HOLDER.get();
        return ctx != null && !"anonymous".equals(ctx.userId());
    }
}
