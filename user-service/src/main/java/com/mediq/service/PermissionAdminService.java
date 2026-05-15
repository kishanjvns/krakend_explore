package com.mediq.service;

import com.mediq.model.RolePermission;
import com.mediq.repository.RolePermissionsRepository;
import com.mediq.security.MediqPermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PermissionAdminService {

    private static final Logger log = LoggerFactory.getLogger(PermissionAdminService.class);

    private final RolePermissionsRepository rolePermissionsRepository;

    public PermissionAdminService(RolePermissionsRepository rolePermissionsRepository) {
        this.rolePermissionsRepository = rolePermissionsRepository;
    }

    @Transactional(readOnly = true)
    public List<RolePermissionDto> getAllRolePermissions() {
        Map<String, List<String>> grouped = rolePermissionsRepository.findAll()
            .stream()
            .collect(Collectors.groupingBy(
                RolePermission::getRoleName,
                Collectors.mapping(RolePermission::getPermission, Collectors.toList())
            ));

        return List.of("PATIENT", "DOCTOR", "NURSE", "ADMIN").stream()
            .map(role -> new RolePermissionDto(role, grouped.getOrDefault(role, List.of())))
            .toList();
    }

    @Transactional
    public void updateRolePermissions(String roleName, List<String> permissions) {
        List<String> invalid = permissions.stream()
            .filter(p -> !MediqPermissions.ALL_PERMISSIONS.contains(p))
            .toList();

        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException("Unknown permissions: " + invalid);
        }

        rolePermissionsRepository.deleteByRoleName(roleName);

        List<RolePermission> newMappings = permissions.stream()
            .map(p -> new RolePermission(roleName, p))
            .toList();
        rolePermissionsRepository.saveAll(newMappings);

        log.info("Updated permissions for role={}: {} entries", roleName, permissions.size());
    }

    @Transactional(readOnly = true)
    public List<String> getAllAvailablePermissions() {
        return MediqPermissions.ALL_PERMISSIONS;
    }
}
