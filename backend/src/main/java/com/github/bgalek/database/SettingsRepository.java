package com.github.bgalek.database;

import org.slf4j.Logger;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Runtime settings that survive a restart.
 * <p>
 * Update-then-insert rather than an upsert: HSQLDB, which the dev profile runs, has no
 * {@code ON CONFLICT} even in its PostgreSQL syntax mode, and these writes are rare and
 * single-threaded enough that the race is not worth a dialect split.
 */
@Repository
public class SettingsRepository {

    private static final Logger logger = getLogger(SettingsRepository.class);

    private final JdbcClient jdbcClient;

    public SettingsRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<String> get(String key) {
        try {
            return jdbcClient.sql("SELECT setting_value FROM app_settings WHERE setting_key = :key")
                    .param("key", key)
                    .query(String.class)
                    .optional();
        } catch (RuntimeException e) {
            logger.warn("Could not read setting '{}'", key, e);
            return Optional.empty();
        }
    }

    public void put(String key, String value) {
        int updated = jdbcClient.sql("""
                        UPDATE app_settings SET setting_value = :value, updated_at = CURRENT_TIMESTAMP
                         WHERE setting_key = :key
                        """)
                .param("value", value).param("key", key)
                .update();
        if (updated == 0) {
            jdbcClient.sql("INSERT INTO app_settings (setting_key, setting_value) VALUES (:key, :value)")
                    .param("key", key).param("value", value)
                    .update();
        }
    }

    public void delete(String key) {
        jdbcClient.sql("DELETE FROM app_settings WHERE setting_key = :key").param("key", key).update();
    }
}
