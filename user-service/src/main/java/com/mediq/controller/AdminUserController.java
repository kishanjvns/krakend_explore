package com.mediq.controller;

import com.mediq.dto.UserResponse;
import com.mediq.model.UserType;
import com.mediq.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('READ_ANY_PROFILE')")
    public ResponseEntity<List<UserResponse>> listAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @PutMapping("/{userId}/activate")
    @PreAuthorize("hasAuthority('DEACTIVATE_USER')")
    public ResponseEntity<UserResponse> activateUser(@PathVariable UUID userId) {
        String adminId = principal();
        return ResponseEntity.ok(userService.activateUser(userId, UUID.fromString(adminId)));
    }

    @PutMapping("/{userId}/deactivate")
    @PreAuthorize("hasAuthority('DEACTIVATE_USER')")
    public ResponseEntity<UserResponse> deactivateUser(@PathVariable UUID userId) {
        String adminId = principal();
        return ResponseEntity.ok(userService.deactivateUser(userId, UUID.fromString(adminId)));
    }

    @PutMapping("/{userId}/role")
    @PreAuthorize("hasAuthority('MANAGE_ROLES')")
    public ResponseEntity<UserResponse> changeRole(
            @PathVariable UUID userId,
            @RequestBody Map<String, String> body) {
        String roleStr = body.get("role");
        if (roleStr == null || roleStr.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        UserType newRole = UserType.valueOf(roleStr.toUpperCase());
        String adminId = principal();
        return ResponseEntity.ok(userService.changeUserRole(userId, newRole,
            UUID.fromString(adminId)));
    }

    private String principal() {
        return (String) SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal();
    }
}
