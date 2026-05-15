package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

public class V5__SeedDefaultUsers extends BaseJavaMigration {

    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();

        // Fix the user_type check constraint to include NURSE
        try (var stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE mediq_users.users DROP CONSTRAINT IF EXISTS users_user_type_check");
            stmt.execute("ALTER TABLE mediq_users.users ADD CONSTRAINT users_user_type_check " +
                         "CHECK (user_type IN ('PATIENT','DOCTOR','NURSE','ADMIN'))");
        }

        seedUser(conn, "admin@mediq.com",    "admin123",  "System", "Admin", "ADMIN",  true);
        seedUser(conn, "dr.mehta@mediq.com", "doctor123", "Raj",    "Mehta", "DOCTOR", false);
        seedUser(conn, "priya@mediq.com",    "nurse123",  "Priya",  "Verma", "NURSE",  false);
    }

    private void seedUser(Connection conn, String email, String password,
                          String firstName, String lastName,
                          String userType, boolean verified) throws Exception {

        try (PreparedStatement check = conn.prepareStatement(
                "SELECT 1 FROM mediq_users.user_contact WHERE contact_value = ?")) {
            check.setString(1, email);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) return;
            }
        }

        String hash   = encoder.encode(password);
        UUID   userId = UUID.randomUUID();

        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO mediq_users.users " +
                "(id, user_type, first_name, last_name, is_active, is_verified, password_hash) " +
                "VALUES (?::uuid, ?, ?, ?, true, ?, ?)")) {
            ins.setString(1, userId.toString());
            ins.setString(2, userType);
            ins.setString(3, firstName);
            ins.setString(4, lastName);
            ins.setBoolean(5, verified);
            ins.setString(6, hash);
            ins.executeUpdate();
        }

        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO mediq_users.user_contact " +
                "(user_id, contact_type, contact_value, is_primary, is_verified) " +
                "VALUES (?::uuid, 'EMAIL', ?, true, true)")) {
            ins.setString(1, userId.toString());
            ins.setString(2, email);
            ins.executeUpdate();
        }
    }
}
