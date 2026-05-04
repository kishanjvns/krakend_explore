package com.mediq.interceptor;

import com.mediq.model.UserContext;

public final class UserContextHolder {

    private static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

    private UserContextHolder() {}

    public static void set(UserContext context) { HOLDER.set(context); }
    public static UserContext get() { return HOLDER.get(); }
    public static void clear() { HOLDER.remove(); }

    public static boolean isAuthenticated() {
        UserContext ctx = HOLDER.get();
        return ctx != null && !"anonymous".equals(ctx.userId());
    }
}
