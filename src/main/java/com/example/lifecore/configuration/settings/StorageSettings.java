package com.example.lifecore.configuration.settings;

import com.example.lifecore.configuration.ConfigReader;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Validated view of storage.yml.
 */
public record StorageSettings(
        Type type,
        String tablePrefix,
        String sqliteFile,
        String host,
        int port,
        String database,
        String username,
        String password,
        boolean useSsl,
        Map<String, String> properties,
        int maximumPoolSize,
        int minimumIdle,
        long connectionTimeoutMs,
        long maxLifetimeMs,
        long keepaliveTimeMs,
        int autosaveIntervalSeconds,
        boolean saveOnChange,
        int loadTimeoutSeconds,
        boolean denyLoginOnLoadFailure
) {

    public enum Type {SQLITE, MYSQL, MARIADB}

    public static StorageSettings read(ConfigReader r) {
        String prefix = r.getString("table-prefix", "lifecore_");
        if (!prefix.matches("[A-Za-z0-9_]{0,32}")) {
            r.problem("table-prefix", "only letters, digits and underscores are allowed, using lifecore_");
            prefix = "lifecore_";
        }
        Map<String, String> props = new LinkedHashMap<>();
        for (String key : r.keys("mysql.properties")) {
            props.put(key, r.getString("mysql.properties." + key, ""));
        }
        int maxPool = r.getInt("pool.maximum-pool-size", 8, 1, 64);
        int minIdle = r.getInt("pool.minimum-idle", 2, 0, 64);
        if (minIdle > maxPool) {
            r.problem("pool.minimum-idle", "greater than maximum-pool-size, using " + maxPool);
            minIdle = maxPool;
        }
        return new StorageSettings(
                r.getEnum("type", Type.class, Type.SQLITE),
                prefix,
                r.getString("sqlite.file", "data/lifecore.db"),
                r.getString("mysql.host", "localhost"),
                r.getInt("mysql.port", 3306, 1, 65535),
                r.getString("mysql.database", "lifecore"),
                r.getString("mysql.username", "root"),
                r.getString("mysql.password", ""),
                r.getBoolean("mysql.use-ssl", false),
                Collections.unmodifiableMap(props),
                maxPool,
                minIdle,
                (long) r.getDouble("pool.connection-timeout-ms", 10000, 250, 600000),
                (long) r.getDouble("pool.max-lifetime-ms", 1800000, 30000, 86400000),
                (long) r.getDouble("pool.keepalive-time-ms", 0, 0, 86400000),
                r.getInt("autosave.interval-seconds", 180, 0, 86400),
                r.getBoolean("autosave.save-on-change", true),
                r.getInt("load-timeout-seconds", 10, 1, 120),
                r.getBoolean("deny-login-on-load-failure", true));
    }
}
