package com.example.lifecore.storage;

import com.example.lifecore.configuration.settings.StorageSettings;
import com.example.lifecore.util.LifeLogger;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * MySQL / MariaDB storage with a HikariCP connection pool.
 * The MariaDB driver is used when present, otherwise the MySQL Connector/J bundled with the server.
 */
public final class MySQLStorage extends SqlStorage {

    private final StorageSettings settings;

    public MySQLStorage(StorageSettings settings, LifeLogger logger) {
        super(settings.tablePrefix(), logger);
        this.settings = settings;
    }

    @Override
    public String describe() {
        return settings.type().name() + " (" + settings.host() + ":" + settings.port() + "/" + settings.database() + ")";
    }

    @Override
    protected HikariDataSource createDataSource() throws StorageException {
        String driver = null;
        String protocol = "mysql";
        if (settings.type() == StorageSettings.Type.MARIADB && classExists("org.mariadb.jdbc.Driver")) {
            driver = "org.mariadb.jdbc.Driver";
            protocol = "mariadb";
        } else if (classExists("com.mysql.cj.jdbc.Driver")) {
            driver = "com.mysql.cj.jdbc.Driver";
        } else if (classExists("com.mysql.jdbc.Driver")) {
            driver = "com.mysql.jdbc.Driver";
        } else if (classExists("org.mariadb.jdbc.Driver")) {
            driver = "org.mariadb.jdbc.Driver";
            protocol = "mariadb";
        }
        if (driver == null) {
            throw new StorageException("No MySQL/MariaDB JDBC driver found on this server.");
        }
        StringJoiner params = new StringJoiner("&");
        params.add("useSSL=" + settings.useSsl());
        for (Map.Entry<String, String> property : settings.properties().entrySet()) {
            params.add(encode(property.getKey()) + "=" + encode(property.getValue()));
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName("LifeCore-" + settings.type().name());
        config.setDriverClassName(driver);
        config.setJdbcUrl("jdbc:" + protocol + "://" + settings.host() + ":" + settings.port() + "/" + settings.database() + "?" + params);
        config.setUsername(settings.username());
        config.setPassword(settings.password());
        config.setMaximumPoolSize(settings.maximumPoolSize());
        config.setMinimumIdle(settings.minimumIdle());
        config.setConnectionTimeout(settings.connectionTimeoutMs());
        config.setMaxLifetime(settings.maxLifetimeMs());
        if (settings.keepaliveTimeMs() > 0) {
            config.setKeepaliveTime(settings.keepaliveTimeMs());
        }
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        try {
            return new HikariDataSource(config);
        } catch (RuntimeException ex) {
            throw new StorageException("Unable to connect to " + describe() + ": " + ex.getMessage(), ex);
        }
    }

    @Override
    protected List<String> schemaStatements() {
        return List.of(
                "CREATE TABLE IF NOT EXISTS " + table("meta") + " (meta_key VARCHAR(64) NOT NULL PRIMARY KEY, "
                        + "meta_value VARCHAR(255) NOT NULL) DEFAULT CHARSET=utf8mb4",
                "CREATE TABLE IF NOT EXISTS " + table("players") + " ("
                        + "uuid CHAR(36) NOT NULL PRIMARY KEY, "
                        + "name VARCHAR(32) NOT NULL, "
                        + "name_lower VARCHAR(32) NOT NULL, "
                        + "hearts DOUBLE NOT NULL, "
                        + "max_hearts DOUBLE NOT NULL DEFAULT -1, "
                        + "kills INT NOT NULL DEFAULT 0, "
                        + "deaths INT NOT NULL DEFAULT 0, "
                        + "revives INT NOT NULL DEFAULT 0, "
                        + "times_revived INT NOT NULL DEFAULT 0, "
                        + "eliminations INT NOT NULL DEFAULT 0, "
                        + "kill_streak INT NOT NULL DEFAULT 0, "
                        + "best_kill_streak INT NOT NULL DEFAULT 0, "
                        + "hearts_gained DOUBLE NOT NULL DEFAULT 0, "
                        + "hearts_lost DOUBLE NOT NULL DEFAULT 0, "
                        + "eliminated TINYINT NOT NULL DEFAULT 0, "
                        + "eliminated_at BIGINT NOT NULL DEFAULT 0, "
                        + "hearts_before_elimination DOUBLE NOT NULL DEFAULT 0, "
                        + "elimination_cause VARCHAR(64) NOT NULL DEFAULT '', "
                        + "eliminated_by VARCHAR(64) NOT NULL DEFAULT '', "
                        + "ban_expires_at BIGINT NOT NULL DEFAULT 0, "
                        + "pending_revive TINYINT NOT NULL DEFAULT 0, "
                        + "revived_at BIGINT NOT NULL DEFAULT 0, "
                        + "revived_by VARCHAR(64) NOT NULL DEFAULT '', "
                        + "last_death_at BIGINT NOT NULL DEFAULT 0, "
                        + "last_death_cause VARCHAR(64) NOT NULL DEFAULT '', "
                        + "last_killer VARCHAR(64) NOT NULL DEFAULT '', "
                        + "first_join BIGINT NOT NULL DEFAULT 0, "
                        + "last_seen BIGINT NOT NULL DEFAULT 0, "
                        + "INDEX idx_name (name_lower), INDEX idx_hearts (hearts), INDEX idx_kills (kills), "
                        + "INDEX idx_deaths (deaths), INDEX idx_revives (revives), INDEX idx_eliminated (eliminated)"
                        + ") DEFAULT CHARSET=utf8mb4",
                "CREATE TABLE IF NOT EXISTS " + table("item_ledger") + " ("
                        + "id CHAR(36) NOT NULL PRIMARY KEY, "
                        + "type VARCHAR(16) NOT NULL, "
                        + "item_value DOUBLE NOT NULL DEFAULT 0, "
                        + "issuer VARCHAR(36) NOT NULL DEFAULT '', "
                        + "created_at BIGINT NOT NULL DEFAULT 0, "
                        + "consumed_by VARCHAR(36) NOT NULL DEFAULT '', "
                        + "consumed_at BIGINT NOT NULL DEFAULT 0) DEFAULT CHARSET=utf8mb4",
                "CREATE TABLE IF NOT EXISTS " + table("beacons") + " ("
                        + "id CHAR(36) NOT NULL PRIMARY KEY, "
                        + "tier VARCHAR(64) NOT NULL, "
                        + "world VARCHAR(64) NOT NULL, "
                        + "x INT NOT NULL, y INT NOT NULL, z INT NOT NULL, "
                        + "owner CHAR(36) NOT NULL, owner_name VARCHAR(32) NOT NULL, "
                        + "target CHAR(36) NOT NULL, target_name VARCHAR(32) NOT NULL, "
                        + "remaining_seconds INT NOT NULL, "
                        + "durability DOUBLE NOT NULL, max_durability DOUBLE NOT NULL, "
                        + "started_at BIGINT NOT NULL, item_id VARCHAR(36) NOT NULL DEFAULT '') DEFAULT CHARSET=utf8mb4");
    }

    @Override
    protected String upsertPlayerSql() {
        StringJoiner updates = new StringJoiner(", ");
        for (String column : UPDATABLE_COLUMNS) {
            updates.add(column + " = VALUES(" + column + ")");
        }
        return "INSERT INTO " + table("players") + " (" + PLAYER_COLUMNS + ") VALUES (" + playerPlaceholders()
                + ") ON DUPLICATE KEY UPDATE " + updates;
    }

    @Override
    protected String upsertBeaconSql() {
        return "REPLACE INTO " + table("beacons") + " (id, tier, world, x, y, z, owner, owner_name, target, "
                + "target_name, remaining_seconds, durability, max_durability, started_at, item_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    @Override
    protected String upsertMetaSql() {
        return "REPLACE INTO " + table("meta") + " (meta_key, meta_value) VALUES (?, ?)";
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException | LinkageError ex) {
            return false;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
