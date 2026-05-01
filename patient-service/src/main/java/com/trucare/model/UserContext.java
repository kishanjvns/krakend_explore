package com.trucare.model;

/**
 * Java 17 FEATURE — Record as a value object for JWT claims.
 *
 * KrakenD validates the JWT and strips the raw token before forwarding
 * requests to backend services. Instead of the raw token, KrakenD injects
 * the decoded JWT claims as plain HTTP headers:
 *
 *   X-User-Id    → the "sub" claim from the JWT (Cognito user ID)
 *   X-User-Email → the "email" claim
 *   X-User-Role  → a custom claim defined in Cognito user pool
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
 *
 *   This is why we disabled Spring Security in application.properties —
 *   re-validating the JWT inside the service would be redundant and slower.
 */
public record UserContext(
        String userId,      // from X-User-Id   (Cognito sub claim)
        String email,       // from X-User-Email
        String role,        // from X-User-Role  (e.g. DOCTOR, NURSE, ADMIN)
        String name         // from X-User-Name
) {
    /**
     * Compact constructor — validates that userId is always present.
     * KrakenD always injects X-User-Id for authenticated endpoints.
     * If it is missing, something is wrong with KrakenD config.
     */
    public UserContext {
        if (userId == null || userId.isBlank()) {
            throw new IllegalStateException(
                "X-User-Id header missing — check KrakenD JWT propagation config");
        }
    }

    /**
     * Convenience factory for anonymous/public endpoints.
     * Used when an endpoint does not require auth (no JWT validation).
     */
    public static UserContext anonymous() {
        return new UserContext("anonymous", null, "GUEST", null);
    }

    /**
     * Role check helpers — avoids string comparison scattered across controllers.
     */
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
