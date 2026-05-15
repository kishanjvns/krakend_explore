package com.mediq.security;

import java.util.List;

public final class MediqPermissions {

    private MediqPermissions() {}

    public static final List<String> ALL_PERMISSIONS = List.of(
        "READ_OWN_PROFILE", "WRITE_OWN_PROFILE",
        "READ_ANY_PROFILE", "WRITE_ANY_PROFILE",
        "READ_DOCTORS", "READ_DOCTOR_AVAILABILITY",
        "READ_PATIENT_PROFILE",
        "WRITE_OWN_APPOINTMENT", "READ_OWN_APPOINTMENT",
        "READ_ANY_APPOINTMENT", "CANCEL_OWN_APPOINTMENT",
        "CANCEL_APPOINTMENT", "CANCEL_ANY_APPOINTMENT",
        "WRITE_APPOINTMENT_SLOT", "CONFIRM_APPOINTMENT",
        "READ_EMR", "WRITE_EMR",
        "READ_OWN_ANALYTICS", "READ_ANALYTICS",
        "READ_OWN_NOTIFICATIONS", "READ_ANY_NOTIFICATIONS",
        "VERIFY_DOCTOR", "DEACTIVATE_USER",
        "SEND_OTP", "VERIFY_OTP", "MANAGE_ROLES"
    );

    // Default mappings are seeded in V4 SQL migration and managed via role_permissions table.
}
