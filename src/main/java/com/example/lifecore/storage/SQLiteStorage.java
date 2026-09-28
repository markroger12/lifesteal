package com.example.lifecore.storage;

import com.example.lifecore.util.LifeLogger;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.StringJoiner;

/**
 * SQLite storage (default). Uses WAL mode and a single pooled connection, which matches
 * SQLite's single-writer model and LifeCore's single database thread.
 */
public final class SQLiteStorage extends SqlStorage {

    private final File file;

    public SQLiteStorage(File file, String prefix, LifeLogger logger) {
        super(prefix, logger);
        this.file = file;
    }

    @Override
    public String describe() {
        return "SQLite (" + file.getName() + ")";
    }

    @Override
    protected HikariDataSource createDataSource() throws StorageException {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new StorageException("Unable to create database folder " + parent);
        }
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ex) {
            throw new StorageException("The SQLite JDBC driver is not available on this server. Use MySQL/MariaDB in storage.yml.", ex);
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName("LifeCore-SQLite");
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(15000);
        config.setConnectionTestQuery("SELECT 1");
        config.addDataSourceProperty("busy_timeout", "5000");
        try {
            return new HikariDataSource(config);
        } catch (RuntimeException ex) {
            throw new StorageException("Unable to open SQLite database " + file + ": " + ex.getMessage(), ex);
        }
    }

    @Override
    protected void onConnectionReady(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA busy_timeout=5000");
        }
    }

    @Override
    protected List<String> schemaStatements() {
        String players = table("players");
        return List.of(
                "CREATE TABLE IF NOT EXISTS " + table("meta") + " (meta_key VARCHAR(64) NOT NULL PRIMARY KEY, meta_value VARCHAR(255) NOT NULL)",
                "CREATE TABLE IF NOT EXISTS " + players + " ("
                        + "uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                        + "name VARCHAR(32) NOT NULL, "
                        + "name_lower VARCHAR(32) NOT NULL, "
                        + "hearts DOUBLE NOT NULL, "
                        + "max_hearts DOUBLE NOT NULL DEFAULT -1, "
                        + "kills INTEGER NOT NULL DEFAULT 0, "
                        + "deaths INTEGER NOT NULL DEFAULT 0, "
                        + "revives INTEGER NOT NULL DEFAULT 0, "
                        + "times_revived INTEGER NOT NULL DEFAULT 0, "
                        + "eliminations INTEGER NOT NULL DEFAULT 0, "
                        + "kill_streak INTEGER NOT NULL DEFAULT 0, "
                        + "best_kill_streak INTEGER NOT NULL DEFAULT 0, "
                        + "hearts_gained DOUBLE NOT NULL DEFAULT 0, "
                        + "hearts_lost DOUBLE NOT NULL DEFAULT 0, "
                        + "eliminated INTEGER NOT NULL DEFAULT 0, "
                        + "eliminated_at BIGINT NOT NULL DEFAULT 0, "
                        + "hearts_before_elimination DOUBLE NOT NULL DEFAULT 0, "
                        + "elimination_cause VARCHAR(64) NOT NULL DEFAULT '', "
                        + "eliminated_by VARCHAR(64) NOT NULL DEFAULT '', "
                        + "ban_expires_at BIGINT NOT NULL DEFAULT 0, "
                        + "pending_revive INTEGER NOT NULL DEFAULT 0, "
                        + "revived_at BIGINT NOT NULL DEFAULT 0, "
                        + "revived_by VARCHAR(64) NOT NULL DEFAULT '', "
                        + "last_death_at BIGINT NOT NULL DEFAULT 0, "
                        + "last_death_cause VARCHAR(64) NOT NULL DEFAULT '', "
                        + "last_killer VARCHAR(64) NOT NULL DEFAULT '', "
                        + "first_join BIGINT NOT NULL DEFAULT 0, "
                        + "last_seen BIGINT NOT NULL DEFAULT 0)",
                "CREATE INDEX IF NOT EXISTS " + prefix + "idx_players_name ON " + players + " (name_lower)",
                "CREATE INDEX IF NOT EXISTS " + prefix + "idx_players_hearts ON " + players + " (hearts)",
                "CREATE INDEX IF NOT EXISTS " + prefix + "idx_players_kills ON " + players + " (kills)",
                "CREATE INDEX IF NOT EXISTS " + prefix + "idx_players_deaths ON " + players + " (deaths)",
                "CREATE INDEX IF NOT EXISTS " + prefix + "idx_players_revives ON " + players + " (revives)",
                "CREATE INDEX IF NOT EXISTS " + prefix + "idx_players_eliminated ON " + players + " (eliminated)",
                "CREATE TABLE IF NOT EXISTS " + table("item_ledger") + " ("
                        + "id VARCHAR(36) NOT NULL PRIMARY KEY, "
                        + "type VARCHAR(16) NOT NULL, "
                        + "item_value DOUBLE NOT NULL DEFAULT 0, "
                        + "issuer VARCHAR(36) NOT NULL DEFAULT '', "
                        + "created_at BIGINT NOT NULL DEFAULT 0, "
                        + "consumed_by VARCHAR(36) NOT NULL DEFAULT '', "
                        + "consumed_at BIGINT NOT NULL DEFAULT 0)",
                "CREATE TABLE IF NOT EXISTS " + table("beacons") + " ("
                        + "id VARCHAR(36) NOT NULL PRIMARY KEY, "
                        + "tier VARCHAR(64) NOT NULL, "
                        + "world VARCHAR(64) NOT NULL, "
                        + "x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL, "
                        + "owner VARCHAR(36) NOT NULL, owner_name VARCHAR(32) NOT NULL, "
                        + "target VARCHAR(36) NOT NULL, target_name VARCHAR(32) NOT NULL, "
                        + "remaining_seconds INTEGER NOT NULL, "
                        + "durability DOUBLE NOT NULL, max_durability DOUBLE NOT NULL, "
                        + "started_at BIGINT NOT NULL, item_id VARCHAR(36) NOT NULL DEFAULT '')");
    }

    @Override
    protected String upsertPlayerSql() {
        StringJoiner updates = new StringJoiner(", ");
        for (String column : UPDATABLE_COLUMNS) {
            updates.add(column + " = excluded." + column);
        }
        return "INSERT INTO " + table("players") + " (" + PLAYER_COLUMNS + ") VALUES (" + playerPlaceholders()
                + ") ON CONFLICT(uuid) DO UPDATE SET " + updates;
    }

    @Override
    protected String upsertBeaconSql() {
        return "INSERT OR REPLACE INTO " + table("beacons") + " (id, tier, world, x, y, z, owner, owner_name, target, "
                + "target_name, remaining_seconds, durability, max_durability, started_at, item_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    @Override
    protected String upsertMetaSql() {
        return "INSERT OR REPLACE INTO " + table("meta") + " (meta_key, meta_value) VALUES (?, ?)";
    }
}
