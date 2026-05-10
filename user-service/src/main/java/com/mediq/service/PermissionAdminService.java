package com.mediq.service;

import com.mediq.security.MediqPermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class PermissionAdminService {

    private static final Logger log = LoggerFactory.getLogger(PermissionAdminService.class);

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${mediq.keycloak.admin-url}")
    private String keycloakAdminUrl;

    @Value("${KEYCLOAK_ADMIN_USERNAME:admin}")
    private String adminUsername;

    @Value("${KEYCLOAK_ADMIN_PASSWORD:admin}")
    private String adminPassword;

    public List<RolePermissionDto> getAllRolePermissions() {
        List<String> roleNames = List.of("PATIENT", "DOCTOR", "NURSE", "ADMIN");
        List<RolePermissionDto> result = new ArrayList<>();
        for (String roleName : roleNames) {
            result.add(new RolePermissionDto(roleName, getRolePermissions(roleName)));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<String> getRolePermissions(String roleName) {
        try {
            String token = getAdminToken();
            String url = keycloakAdminUrl + "/admin/realms/mediq/roles/" + roleName;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            Map<String, Object> role = response.getBody();
            if (role == null) return List.of();

            Map<String, List<String>> attrs = (Map<String, List<String>>) role.get("attributes");
            if (attrs == null || !attrs.containsKey("permissions")) return List.of();
            return attrs.get("permissions");

        } catch (Exception e) {
            log.error("Failed to get permissions for role {}: {}", roleName, e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    public void updateRolePermissions(String roleName, List<String> permissions) {
        List<String> invalid = permissions.stream()
            .filter(p -> !MediqPermissions.ALL_PERMISSIONS.contains(p))
            .toList();

        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException("Unknown permissions: " + invalid);
        }

        try {
            String token = getAdminToken();
            String url = keycloakAdminUrl + "/admin/realms/mediq/roles/" + roleName;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map> getResponse = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            Map<String, Object> role = new HashMap<>(getResponse.getBody());
            Map<String, List<String>> attrs =
                (Map<String, List<String>>) role.getOrDefault("attributes", new HashMap<>());
            attrs.put("permissions", permissions);
            role.put("attributes", attrs);

            restTemplate.exchange(url, HttpMethod.PUT,
                new HttpEntity<>(role, headers), Void.class);

            log.info("Updated permissions for role {}: {}", roleName, permissions);

        } catch (Exception e) {
            log.error("Failed to update permissions for role {}: {}", roleName, e.getMessage());
            throw new RuntimeException("Failed to update role permissions", e);
        }
    }

    public List<String> getAllAvailablePermissions() {
        return MediqPermissions.ALL_PERMISSIONS;
    }

    private String getAdminToken() {
        String url = keycloakAdminUrl + "/realms/master/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String body = "grant_type=password&client_id=admin-cli" +
                      "&username=" + adminUsername +
                      "&password=" + adminPassword;

        ResponseEntity<Map> response = restTemplate.exchange(
            url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

        return (String) response.getBody().get("access_token");
    }
}
