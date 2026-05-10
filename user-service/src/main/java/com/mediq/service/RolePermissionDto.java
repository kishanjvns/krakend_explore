package com.mediq.service;

import java.util.List;

public record RolePermissionDto(
    String roleName,
    List<String> permissions
) {}
