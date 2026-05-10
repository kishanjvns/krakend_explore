package com.mediq.controller;

import com.mediq.service.PermissionAdminService;
import com.mediq.service.RolePermissionDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class PermissionAdminController {

    private final PermissionAdminService permissionAdminService;

    public PermissionAdminController(PermissionAdminService permissionAdminService) {
        this.permissionAdminService = permissionAdminService;
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('MANAGE_ROLES')")
    public ResponseEntity<List<RolePermissionDto>> getAllRoles() {
        return ResponseEntity.ok(permissionAdminService.getAllRolePermissions());
    }

    @PutMapping("/roles/{roleName}/permissions")
    @PreAuthorize("hasAuthority('MANAGE_ROLES')")
    public ResponseEntity<Void> updateRolePermissions(
            @PathVariable String roleName,
            @RequestBody Map<String, List<String>> body) {
        permissionAdminService.updateRolePermissions(roleName, body.get("permissions"));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('MANAGE_ROLES')")
    public ResponseEntity<List<String>> getAllPermissions() {
        return ResponseEntity.ok(permissionAdminService.getAllAvailablePermissions());
    }
}
