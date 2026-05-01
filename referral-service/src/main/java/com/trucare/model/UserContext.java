package com.trucare.model;

/**
 * Java 17 FEATURE — Record as a value object for JWT claims.
 *
 * KrakenD validates the JWT and strips the raw token before forwarding
 * requests to backend services. Instead of the raw token, KrakenD injects
 * the decoded JWT claims as plain HTTP headers:
 *
 *   X-User-Id    → the "sub" claim from the JWT (Keycloak / Cognito user ID)
 *   X-User-Email → the "email" claim
 *   X-User-Role  → the "role" claim
 *   X-User-Name  → the "name" claim
 *
 * This record represents those forwarded claims in a type-safe way.
 * It is populated by JwtClaimsInterceptor from the incoming request headers.
 *
 * Interview note:
 *   This pattern is called "claim propagation". The gateway validates auth
 *   once at the edge and propagates identity downstream as trusted headers.
 *   Backend services trust these headers because they are only reachable
 *   from inside the cluster (ClusterIP) — external callers cannot forge them.
 */
public record UserContext(
        String userId,
        String email,
        String role,
        String name
) {
    public UserContext {
        if (userId == null || userId.isBlank()) {
            throw new IllegalStateException(
                "X-User-Id header missing — check KrakenD JWT propagation config");
        }
    }

    public static UserContext anonymous() {
        return new UserContext("anonymous", null, "GUEST", null);
    }

    public boolean isDoctor() {
        return "DOCTOR".equalsIgnoreCase(role);
    }

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }

    public boolean isNurse() {
        return "NURSE".equalsIgnoreCase(role);
    }
}
