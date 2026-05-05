package com.mediq.keycloak;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
public class KeycloakAdminClient {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminClient.class);

    private final RestTemplate restTemplate;
    private final String adminUrl;
    private final String realm;

    public KeycloakAdminClient(
            @Value("${mediq.keycloak.admin-url}") String adminUrl,
            @Value("${mediq.keycloak.realm}") String realm) {
        this.restTemplate = new RestTemplate();
        this.adminUrl = adminUrl;
        this.realm = realm;
    }

    public String createUser(String email, String fullName, String role) {
        String token = getAdminToken();
        String url = adminUrl + "/admin/realms/" + realm + "/users";

        Map<String, Object> body = Map.of(
            "username", email,
            "email", email,
            "firstName", fullName.split(" ")[0],
            "lastName", fullName.contains(" ") ? fullName.split(" ")[1] : "",
            "enabled", true,
            "emailVerified", false,
            "realmRoles", List.of(role)
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Void> response = restTemplate.exchange(
            url, HttpMethod.POST, new HttpEntity<>(body, headers), Void.class);

        String location = response.getHeaders().getFirst("Location");
        if (location == null) throw new RuntimeException("No Location header from Keycloak");

        String keycloakId = location.substring(location.lastIndexOf('/') + 1);
        log.info("Created Keycloak user: keycloakId={}", keycloakId);
        return keycloakId;
    }

    public void disableUser(String keycloakId) {
        String token = getAdminToken();
        String url = adminUrl + "/admin/realms/" + realm + "/users/" + keycloakId;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of("enabled", false);
        restTemplate.exchange(url, HttpMethod.PUT,
            new HttpEntity<>(body, headers), Void.class);
    }

    private String getAdminToken() {
        // TODO: Implement client_credentials grant flow in M2.x task
        return "TODO-implement-admin-token-fetch";
    }
}
