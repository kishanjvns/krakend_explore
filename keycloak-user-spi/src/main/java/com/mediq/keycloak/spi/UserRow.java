package com.mediq.keycloak.spi;

import java.util.List;
import java.util.UUID;

public class UserRow {
    public final UUID   id;
    public final String email;
    public final String firstName;
    public final String lastName;
    public final String passwordHash;
    public final String userType;
    public final boolean active;
    public final List<String> permissions;

    public UserRow(UUID id, String email, String firstName, String lastName,
                   String passwordHash, String userType, boolean active,
                   List<String> permissions) {
        this.id           = id;
        this.email        = email;
        this.firstName    = firstName;
        this.lastName     = lastName;
        this.passwordHash = passwordHash;
        this.userType     = userType;
        this.active       = active;
        this.permissions  = permissions;
    }
}
