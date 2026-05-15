package com.mediq.keycloak.spi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.UserLookupProvider;
import org.mindrot.jbcrypt.BCrypt;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MediqUserStorageProvider
        implements UserStorageProvider, UserLookupProvider, CredentialInputValidator {

    private static final Logger log = LoggerFactory.getLogger(MediqUserStorageProvider.class);

    private static final String SQL_BY_EMAIL =
        "SELECT u.id, u.first_name, u.last_name, u.is_active, u.user_type, u.password_hash, " +
        "       uc.contact_value AS email " +
        "FROM {schema}.users u " +
        "JOIN {schema}.user_contact uc ON uc.user_id = u.id " +
        "                             AND uc.contact_type = 'EMAIL' " +
        "                             AND uc.is_primary = true " +
        "WHERE uc.contact_value = ?";

    private static final String SQL_BY_ID =
        "SELECT u.id, u.first_name, u.last_name, u.is_active, u.user_type, u.password_hash, " +
        "       uc.contact_value AS email " +
        "FROM {schema}.users u " +
        "JOIN {schema}.user_contact uc ON uc.user_id = u.id " +
        "                             AND uc.contact_type = 'EMAIL' " +
        "                             AND uc.is_primary = true " +
        "WHERE u.id = ?::uuid";

    private static final String SQL_PERMISSIONS =
        "SELECT permission FROM {schema}.role_permissions WHERE role_name = ?";

    private final KeycloakSession session;
    private final RealmModel      realm;
    private final ComponentModel  model;
    private final DataSource      dataSource;
    private final String          schema;

    public MediqUserStorageProvider(KeycloakSession session, RealmModel realm,
                                    ComponentModel model, DataSource dataSource,
                                    String schema) {
        this.session    = session;
        this.realm      = realm;
        this.model      = model;
        this.dataSource = dataSource;
        this.schema     = schema;
    }

    @Override
    public UserModel getUserByUsername(RealmModel realm, String username) {
        return findByEmail(realm, username);
    }

    @Override
    public UserModel getUserByEmail(RealmModel realm, String email) {
        return findByEmail(realm, email);
    }

    @Override
    public UserModel getUserById(RealmModel realm, String id) {
        String externalId = StorageId.externalId(id);
        String sql = SQL_BY_ID.replace("{schema}", schema);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, externalId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return toAdapter(realm, rs, conn);
                }
            }
        } catch (SQLException e) {
            log.error("getUserById failed for id={}: {}", id, e.getMessage());
        }
        return null;
    }

    @Override
    public boolean supportsCredentialType(String credentialType) {
        return PasswordCredentialModel.TYPE.equals(credentialType);
    }

    @Override
    public boolean isConfiguredFor(RealmModel realm, UserModel user, String credentialType) {
        return supportsCredentialType(credentialType);
    }

    @Override
    public boolean isValid(RealmModel realm, UserModel user, CredentialInput input) {
        if (!supportsCredentialType(input.getType())) return false;
        String candidatePassword = input.getChallengeResponse();

        String sql = SQL_BY_EMAIL.replace("{schema}", schema);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getEmail());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String hash = rs.getString("password_hash");
                    if (hash == null || hash.isBlank()) return false;
                    return BCrypt.checkpw(candidatePassword, hash);
                }
            }
        } catch (SQLException e) {
            log.error("isValid failed for user={}: {}", user.getEmail(), e.getMessage());
        }
        return false;
    }

    @Override
    public void close() {}

    private UserModel findByEmail(RealmModel realm, String email) {
        String sql = SQL_BY_EMAIL.replace("{schema}", schema);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return toAdapter(realm, rs, conn);
                }
            }
        } catch (SQLException e) {
            log.error("findByEmail failed for email={}: {}", email, e.getMessage());
        }
        return null;
    }

    private MediqUserAdapter toAdapter(RealmModel realm, ResultSet rs, Connection conn)
            throws SQLException {
        UUID   id           = UUID.fromString(rs.getString("id"));
        String firstName    = rs.getString("first_name");
        String lastName     = rs.getString("last_name");
        boolean active      = rs.getBoolean("is_active");
        String userType     = rs.getString("user_type");
        String passwordHash = rs.getString("password_hash");
        String email        = rs.getString("email");

        List<String> permissions = loadPermissions(conn, userType);

        UserRow row = new UserRow(id, email, firstName, lastName,
                                  passwordHash, userType, active, permissions);
        return new MediqUserAdapter(session, realm, model, row);
    }

    private List<String> loadPermissions(Connection conn, String roleName) throws SQLException {
        List<String> perms = new ArrayList<>();
        String sql = SQL_PERMISSIONS.replace("{schema}", schema);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roleName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    perms.add(rs.getString("permission"));
                }
            }
        }
        return perms;
    }
}
