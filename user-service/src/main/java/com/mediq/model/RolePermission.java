package com.mediq.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "role_permissions", schema = "mediq_users")
public class RolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "role_name", nullable = false, length = 20)
    private String roleName;

    @Column(name = "permission", nullable = false, length = 100)
    private String permission;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public RolePermission() {}

    public RolePermission(String roleName, String permission) {
        this.roleName   = roleName;
        this.permission = permission;
    }

    public UUID getId()            { return id; }
    public String getRoleName()    { return roleName; }
    public String getPermission()  { return permission; }
    public Instant getCreatedAt()  { return createdAt; }

    public void setRoleName(String roleName)     { this.roleName = roleName; }
    public void setPermission(String permission) { this.permission = permission; }
}
