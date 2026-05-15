package com.mediq.keycloak.spi;

public final class MediqUserStorageConstants {
    private MediqUserStorageConstants() {}

    public static final String PROVIDER_ID = "mediq-user-storage";

    public static final String CFG_DB_URL      = "db.url";
    public static final String CFG_DB_USER     = "db.user";
    public static final String CFG_DB_PASSWORD = "db.password";
    public static final String CFG_DB_SCHEMA   = "db.schema";

    public static final String DEFAULT_SCHEMA  = "mediq_users";
}
