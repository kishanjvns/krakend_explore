package com.mediq.keycloak.spi;

import org.keycloak.component.ComponentModel;
import org.keycloak.credential.UserCredentialManager;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.SubjectCredentialManager;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.adapter.AbstractUserAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MediqUserAdapter extends AbstractUserAdapter {

    private final UserRow row;

    public MediqUserAdapter(KeycloakSession session, RealmModel realm,
                             ComponentModel model, UserRow row) {
        super(session, realm, model);
        this.row = row;
    }

    @Override
    public String getId() {
        return StorageId.keycloakId(storageProviderModel, row.id.toString());
    }

    @Override
    public String getUsername() { return row.email; }

    @Override
    public void setUsername(String username) {}

    @Override
    public String getEmail() { return row.email; }

    @Override
    public void setEmail(String email) {}

    @Override
    public String getFirstName() { return row.firstName; }

    @Override
    public void setFirstName(String firstName) {}

    @Override
    public String getLastName() { return row.lastName; }

    @Override
    public void setLastName(String lastName) {}

    @Override
    public boolean isEnabled() { return row.active; }

    @Override
    public boolean isEmailVerified() { return true; }

    @Override
    public SubjectCredentialManager credentialManager() {
        return new UserCredentialManager(session, realm, this);
    }

    @Override
    protected Set<RoleModel> getRoleMappingsInternal() {
        RoleModel role = realm.getRole(row.userType);
        if (role == null) return Set.of();
        return Set.of(role);
    }

    // Override to prevent Keycloak's default-roles-mediq composite (offline_access,
    // uma_authorization) from being merged in — SPI users get only their business role.
    @Override
    public Set<RoleModel> getRoleMappings() {
        return getRoleMappingsInternal();
    }

    @Override
    public Map<String, List<String>> getAttributes() {
        Map<String, List<String>> attrs = new HashMap<>();
        attrs.put("username",    List.of(row.email));
        attrs.put("email",       List.of(row.email));
        attrs.put("firstName",   List.of(row.firstName));
        attrs.put("lastName",    List.of(row.lastName));
        attrs.put("userId",      List.of(row.id.toString()));
        attrs.put("userType",    List.of(row.userType));
        attrs.put("permissions", new ArrayList<>(row.permissions));
        return attrs;
    }
}
