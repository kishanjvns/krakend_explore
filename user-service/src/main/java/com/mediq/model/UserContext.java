package com.mediq.model;

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

    public boolean isDoctor() { return "DOCTOR".equalsIgnoreCase(role); }
    public boolean isAdmin() { return "ADMIN".equalsIgnoreCase(role); }
    public boolean isNurse() { return "NURSE".equalsIgnoreCase(role); }
}
