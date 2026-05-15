package com.mediq.keycloak.spi;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.keycloak.component.ComponentModel;
import org.keycloak.component.ComponentValidationException;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.storage.UserStorageProviderFactory;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MediqUserStorageProviderFactory
        implements UserStorageProviderFactory<MediqUserStorageProvider> {

    private static final Logger log = LoggerFactory.getLogger(MediqUserStorageProviderFactory.class);

    private static final List<ProviderConfigProperty> CONFIG_PROPERTIES = Arrays.asList(
        prop(MediqUserStorageConstants.CFG_DB_URL,      "DB URL",      "JDBC URL",          ProviderConfigProperty.STRING_TYPE,   "jdbc:postgresql://postgres-service:5432/mediq_users"),
        prop(MediqUserStorageConstants.CFG_DB_USER,     "DB Username", "DB username",        ProviderConfigProperty.STRING_TYPE,   "mediq"),
        prop(MediqUserStorageConstants.CFG_DB_PASSWORD, "DB Password", "DB password",        ProviderConfigProperty.PASSWORD,      "mediq"),
        prop(MediqUserStorageConstants.CFG_DB_SCHEMA,   "DB Schema",   "PostgreSQL schema",  ProviderConfigProperty.STRING_TYPE,   "mediq_users")
    );

    private static ProviderConfigProperty prop(String name, String label, String helpText,
                                                String type, Object defaultValue) {
        ProviderConfigProperty p = new ProviderConfigProperty();
        p.setName(name);
        p.setLabel(label);
        p.setHelpText(helpText);
        p.setType(type);
        p.setDefaultValue(defaultValue);
        return p;
    }

    private final Map<String, HikariDataSource> pools = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return MediqUserStorageConstants.PROVIDER_ID;
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG_PROPERTIES;
    }

    @Override
    public MediqUserStorageProvider create(KeycloakSession session, ComponentModel model) {
        DataSource ds = pools.computeIfAbsent(model.getId(), id -> buildPool(model));
        String schema = model.get(MediqUserStorageConstants.CFG_DB_SCHEMA,
                                   MediqUserStorageConstants.DEFAULT_SCHEMA);
        return new MediqUserStorageProvider(session, session.getContext().getRealm(),
                                            model, ds, schema);
    }

    @Override
    public void validateConfiguration(KeycloakSession session, RealmModel realm,
                                       ComponentModel model) throws ComponentValidationException {
        String url = model.get(MediqUserStorageConstants.CFG_DB_URL);
        if (url == null || url.isBlank()) {
            throw new ComponentValidationException("DB URL is required");
        }
    }

    @Override
    public void onUpdate(KeycloakSession session, RealmModel realm,
                          ComponentModel oldModel, ComponentModel newModel) {
        HikariDataSource old = pools.remove(oldModel.getId());
        if (old != null && !old.isClosed()) old.close();
    }

    @Override
    public void close() {
        pools.values().forEach(ds -> { if (!ds.isClosed()) ds.close(); });
        pools.clear();
    }

    private HikariDataSource buildPool(ComponentModel model) {
        String url      = model.get(MediqUserStorageConstants.CFG_DB_URL,
                                    "jdbc:postgresql://postgres-service:5432/mediq_users");
        String user     = model.get(MediqUserStorageConstants.CFG_DB_USER,     "mediq");
        String password = model.get(MediqUserStorageConstants.CFG_DB_PASSWORD, "mediq");

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setUsername(user);
        cfg.setPassword(password);
        cfg.setMaximumPoolSize(5);
        cfg.setMinimumIdle(1);
        cfg.setConnectionTimeout(3000);
        cfg.setPoolName("mediq-spi-pool-" + model.getId().substring(0, 8));

        log.info("Building Mediq SPI connection pool → {}", url);
        return new HikariDataSource(cfg);
    }
}
