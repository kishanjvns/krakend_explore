package com.mediq.repository;

import com.mediq.model.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface RolePermissionsRepository extends JpaRepository<RolePermission, UUID> {

    List<RolePermission> findByRoleName(String roleName);

    void deleteByRoleName(String roleName);

    @Query("SELECT DISTINCT rp.roleName FROM RolePermission rp")
    List<String> findDistinctRoleNames();
}
