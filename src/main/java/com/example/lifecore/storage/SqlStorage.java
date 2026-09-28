package com.example.lifecore.storage;

import com.example.lifecore.api.LeaderboardEntry;
import com.example.lifecore.api.LeaderboardType;
import com.example.lifecore.model.BeaconRecord;
import com.example.lifecore.model.EliminatedPlayer;
import com.example.lifecore.model.PlayerSnapshot;
import com.example.lifecore.util.LifeLogger;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared JDBC implementation for SQLite and MySQL/MariaDB. Dialect differences (DDL and
 * upsert syntax) are provided by subclasses. All statements are prepared; the only dynamic SQL
 * fragments are the validated table prefix and fixed column names from {@link LeaderboardType}.
 */
public abstract class SqlStorage implements StorageProvider {

    public static final int SCHEMA_VERSION = 1;

    protected static final String PLAYER_COLUMNS = "uuid, name, name_lower, hearts, max_hearts, kills, deaths, revives, "
            + "times_revived, eliminations, kill_streak, best_kill_streak, hearts_gained, hearts_lost, eliminated, "
            + "eliminated_at, hearts_before_elimination, elimination_cause, eliminated_by, ban_expires_at, pending_revive, "
            + "revived_at, revived_by, last_death_at, last_death_cause, last_killer, first_join, last_seen";
    protected static final String[] UPDATABLE_COLUMNS = {"name", "name_lower", "hearts", "max_hearts", "kills", "deaths",
            "revives", "times_revived", "eliminations", "kill_streak", "best_kill_streak", "hearts_gained", "hearts_lost",
            "eliminated", "eliminated_at", "hearts_before_elimination", "elimination_cause", "eliminated_by",
            "ban_expires_at", "pending_revive", "revived_at", "revived_by", "last_death_at", "last_death_cause",
            "last_killer", "first_join", "last_seen"};

    protected final String prefix;
    protected final LifeLogger logger;
    protected HikariDataSource dataSource;

    protected SqlStorage(String prefix, LifeLogger logger) {
        this.prefix = prefix;
        this.logger = logger;
    }

    /** Creates and configures the connection pool. */
    protected abstract HikariDataSource createDataSource() throws StorageException;

    /** @return DDL statements creating all tables (idempotent). */
    protected abstract List<String> schemaStatements();

    /** @return upsert statement for the players table with {@link #PLAYER_COLUMNS} placeholders. */
    protected abstract String upsertPlayerSql();

    protected abstract String upsertBeaconSql();

    protected abstract String upsertMetaSql();

    protected String table(String name) {
        return prefix + name;
    }

    @Override
    public void init() throws StorageException {
        this.dataSource = createDataSource();
        try (Connection connection = dataSource.getConnection()) {
            onConnectionReady(connection);
            try (Statement statement = connection.createStatement()) {
                for (String sql : schemaStatements()) {
                    statement.execute(sql);
                }
            }
            int version = readSchemaVersion(connection);
            if (version > SCHEMA_VERSION) {
                logger.warn("[storage] Database schema version " + version + " is newer than this build supports ("
                        + SCHEMA_VERSION + "). Continuing, but consider updating LifeCore.");
            } else if (version < SCHEMA_VERSION) {
                try (PreparedStatement ps = connection.prepareStatement(upsertMetaSql())) {
                    ps.setString(1, "schema_version");
                    ps.setString(2, String.valueOf(SCHEMA_VERSION));
                    ps.executeUpdate();
                }
            }
        } catch (SQLException ex) {
            throw new StorageException("Failed to initialise the database schema: " + ex.getMessage(), ex);
        }
    }

    /** Hook for dialect specific connection setup (e.g. SQLite pragmas). */
    protected void onConnectionReady(Connection connection) throws SQLException {
    }

    private int readSchemaVersion(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT meta_value FROM " + table("meta") + " WHERE meta_key = ?")) {
            ps.setString(1, "schema_version");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    try {
                        return Integer.parseInt(rs.getString(1));
                    } catch (NumberFormatException ex) {
                        return 0;
                    }
                }
            }
        }
        return 0;
    }

    // ------------------------------------------------------------------ players

    @Override
    public Optional<PlayerSnapshot> loadPlayer(UUID uuid) throws StorageException {
        String sql = "SELECT " + PLAYER_COLUMNS + " FROM " + table("players") + " WHERE uuid = ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(readPlayer(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new StorageException("Failed to load player " + uuid + ": " + ex.getMessage(), ex);
        }
    }

    @Override
    public Optional<PlayerSnapshot> loadPlayerByName(String name) throws StorageException {
        String sql = "SELECT " + PLAYER_COLUMNS + " FROM " + table("players") + " WHERE name_lower = ? ORDER BY last_seen DESC LIMIT 1";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name.toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(readPlayer(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new StorageException("Failed to load player " + name + ": " + ex.getMessage(), ex);
        }
    }

    @Override
    public void savePlayer(PlayerSnapshot snapshot) throws StorageException {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(upsertPlayerSql())) {
            bindPlayer(ps, snapshot);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new StorageException("Failed to save player " + snapshot.uuid() + ": " + ex.getMessage(), ex);
        }
    }

    @Override
    public void savePlayers(Collection<PlayerSnapshot> snapshots) throws StorageException {
        if (snapshots.isEmpty()) {
            return;
        }
        try (Connection c = dataSource.getConnection()) {
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(upsertPlayerSql())) {
                for (PlayerSnapshot snapshot : snapshots) {
                    bindPlayer(ps, snapshot);
                    ps.addBatch();
                }
                ps.executeBatch();
                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(autoCommit);
            }
        } catch (SQLException ex) {
            throw new StorageException("Failed to save " + snapshots.size() + " players: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean deletePlayer(UUID uuid) throws StorageException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM " + table("players") + " WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new StorageException("Failed to delete player " + uuid + ": " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<LeaderboardEntry> top(LeaderboardType type, int limit, boolean excludeEliminated) throws StorageException {
        String where = excludeEliminated && type == LeaderboardType.HEARTS ? " WHERE eliminated = 0" : "";
        String sql = "SELECT uuid, name, " + type.column() + " FROM " + table("players") + where
                + " ORDER BY " + type.column() + " DESC, name_lower ASC LIMIT ?";
        List<LeaderboardEntry> entries = new ArrayList<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                int position = 1;
                while (rs.next()) {
                    UUID uuid = parseUuid(rs.getString(1));
                    if (uuid == null) {
                        continue;
                    }
                    entries.add(new LeaderboardEntry(position++, uuid, rs.getString(2), rs.getDouble(3)));
                }
            }
        } catch (SQLException ex) {
            throw new StorageException("Failed to query leaderboard " + type + ": " + ex.getMessage(), ex);
        }
        return entries;
    }

    @Override
    public List<EliminatedPlayer> listEliminated(int limit) throws StorageException {
        String sql = "SELECT uuid, name, eliminated_at, ban_expires_at, elimination_cause FROM " + table("players")
                + " WHERE eliminated = 1 ORDER BY eliminated_at DESC LIMIT ?";
        List<EliminatedPlayer> list = new ArrayList<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = parseUuid(rs.getString(1));
                    if (uuid != null) {
                        list.add(new EliminatedPlayer(uuid, rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getString(5)));
                    }
                }
            }
        } catch (SQLException ex) {
            throw new StorageException("Failed to list eliminated players: " + ex.getMessage(), ex);
        }
        return list;
    }

    @Override
    public List<String> findNames(String prefixText, int limit) throws StorageException {
        String sql = "SELECT name FROM " + table("players") + " WHERE name_lower LIKE ? ESCAPE '!' ORDER BY last_seen DESC LIMIT ?";
        String escaped = prefixText.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
        List<String> names = new ArrayList<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, escaped + "%");
            ps.setInt(2, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString(1));
                }
            }
        } catch (SQLException ex) {
            throw new StorageException("Failed to search names: " + ex.getMessage(), ex);
        }
        return names;
    }

    @Override
    public int countPlayers() throws StorageException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM " + table("players"));
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException ex) {
            throw new StorageException("Failed to count players: " + ex.getMessage(), ex);
        }
    }

    // ------------------------------------------------------------------ item ledger

    @Override
    public void registerItem(UUID id, String type, double value, UUID issuer, long createdAt) throws StorageException {
        String sql = "INSERT INTO " + table("item_ledger") + " (id, type, item_value, issuer, created_at, consumed_by, consumed_at) "
                + "VALUES (?, ?, ?, ?, ?, '', 0)";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            ps.setString(2, type);
            ps.setDouble(3, value);
            ps.setString(4, issuer == null ? "" : issuer.toString());
            ps.setLong(5, createdAt);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new StorageException("Failed to register item " + id + ": " + ex.getMessage(), ex);
        }
    }

    @Override
    public ConsumeResult consumeItem(UUID id, String type, double value, UUID consumer, long time) throws StorageException {
        String update = "UPDATE " + table("item_ledger") + " SET consumed_by = ?, consumed_at = ? WHERE id = ? AND consumed_at = 0";
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(update)) {
                ps.setString(1, consumer.toString());
                ps.setLong(2, Math.max(1L, time));
                ps.setString(3, id.toString());
                if (ps.executeUpdate() == 1) {
                    return ConsumeResult.CONSUMED;
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT consumed_at FROM " + table("item_ledger") + " WHERE id = ?")) {
                ps.setString(1, id.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return ConsumeResult.ALREADY_CONSUMED;
                    }
                }
            }
            // A correctly signed item whose issue record never reached the database
            // (e.g. the database was down). Record it as consumed; the primary key makes this atomic.
            String insert = "INSERT INTO " + table("item_ledger") + " (id, type, item_value, issuer, created_at, consumed_by, consumed_at) "
                    + "VALUES (?, ?, ?, '', 0, ?, ?)";
            try (PreparedStatement ps = c.prepareStatement(insert)) {
                ps.setString(1, id.toString());
                ps.setString(2, type);
                ps.setDouble(3, value);
                ps.setString(4, consumer.toString());
                ps.setLong(5, Math.max(1L, time));
                ps.executeUpdate();
                return ConsumeResult.CONSUMED;
            } catch (SQLException duplicate) {
                if (isConstraintViolation(duplicate)) {
                    return ConsumeResult.ALREADY_CONSUMED;
                }
                throw duplicate;
            }
        } catch (SQLException ex) {
            throw new StorageException("Failed to consume item " + id + ": " + ex.getMessage(), ex);
        }
    }

    protected boolean isConstraintViolation(SQLException ex) {
        String state = ex.getSQLState();
        return (state != null && state.startsWith("23")) || ex.getErrorCode() == 19 || ex.getErrorCode() == 1062;
    }

    // ------------------------------------------------------------------ beacons

    @Override
    public List<BeaconRecord> loadBeacons() throws StorageException {
        String sql = "SELECT id, tier, world, x, y, z, owner, owner_name, target, target_name, remaining_seconds, durability, "
                + "max_durability, started_at, item_id FROM " + table("beacons");
        List<BeaconRecord> list = new ArrayList<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID id = parseUuid(rs.getString(1));
                UUID owner = parseUuid(rs.getString(7));
                UUID target = parseUuid(rs.getString(9));
                if (id == null || owner == null || target == null) {
                    logger.warn("[storage] Skipping corrupted beacon row " + rs.getString(1));
                    continue;
                }
                list.add(new BeaconRecord(id, rs.getString(2), rs.getString(3), rs.getInt(4), rs.getInt(5), rs.getInt(6),
                        owner, rs.getString(8), target, rs.getString(10), rs.getInt(11), rs.getDouble(12), rs.getDouble(13),
                        rs.getLong(14), parseUuid(rs.getString(15))));
            }
        } catch (SQLException ex) {
            throw new StorageException("Failed to load beacons: " + ex.getMessage(), ex);
        }
        return list;
    }

    @Override
    public void saveBeacon(BeaconRecord r) throws StorageException {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(upsertBeaconSql())) {
            ps.setString(1, r.id().toString());
            ps.setString(2, r.tier());
            ps.setString(3, r.world());
            ps.setInt(4, r.x());
            ps.setInt(5, r.y());
            ps.setInt(6, r.z());
            ps.setString(7, r.owner().toString());
            ps.setString(8, r.ownerName());
            ps.setString(9, r.target().toString());
            ps.setString(10, r.targetName());
            ps.setInt(11, r.remainingSeconds());
            ps.setDouble(12, r.durability());
            ps.setDouble(13, r.maxDurability());
            ps.setLong(14, r.startedAt());
            ps.setString(15, r.itemId() == null ? "" : r.itemId().toString());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new StorageException("Failed to save beacon " + r.id() + ": " + ex.getMessage(), ex);
        }
    }

    @Override
    public void deleteBeacon(UUID id) throws StorageException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM " + table("beacons") + " WHERE id = ?")) {
            ps.setString(1, id.toString());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new StorageException("Failed to delete beacon " + id + ": " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean ping() {
        if (dataSource == null || dataSource.isClosed()) {
            return false;
        }
        try (Connection c = dataSource.getConnection()) {
            return c.isValid(3);
        } catch (SQLException ex) {
            return false;
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    // ------------------------------------------------------------------ mapping

    protected void bindPlayer(PreparedStatement ps, PlayerSnapshot s) throws SQLException {
        int i = 1;
        ps.setString(i++, s.uuid().toString());
        ps.setString(i++, truncate(s.name(), 32));
        ps.setString(i++, truncate(s.name(), 32).toLowerCase(Locale.ROOT));
        ps.setDouble(i++, s.hearts());
        ps.setDouble(i++, s.maxHeartsOverride());
        ps.setInt(i++, s.kills());
        ps.setInt(i++, s.deaths());
        ps.setInt(i++, s.revives());
        ps.setInt(i++, s.timesRevived());
        ps.setInt(i++, s.eliminations());
        ps.setInt(i++, s.killStreak());
        ps.setInt(i++, s.bestKillStreak());
        ps.setDouble(i++, s.heartsGained());
        ps.setDouble(i++, s.heartsLost());
        ps.setInt(i++, s.eliminated() ? 1 : 0);
        ps.setLong(i++, s.eliminatedAt());
        ps.setDouble(i++, s.heartsBeforeElimination());
        ps.setString(i++, truncate(s.eliminationCause(), 64));
        ps.setString(i++, truncate(s.eliminatedBy(), 64));
        ps.setLong(i++, s.banExpiresAt());
        ps.setInt(i++, s.pendingRevive() ? 1 : 0);
        ps.setLong(i++, s.revivedAt());
        ps.setString(i++, truncate(s.revivedBy(), 64));
        ps.setLong(i++, s.lastDeathAt());
        ps.setString(i++, truncate(s.lastDeathCause(), 64));
        ps.setString(i++, truncate(s.lastKiller(), 64));
        ps.setLong(i++, s.firstJoin());
        ps.setLong(i, s.lastSeen());
    }

    protected PlayerSnapshot readPlayer(ResultSet rs) throws SQLException {
        UUID uuid = parseUuid(rs.getString("uuid"));
        if (uuid == null) {
            throw new SQLException("Corrupted player row: invalid uuid '" + rs.getString("uuid") + "'");
        }
        return new PlayerSnapshot(
                uuid,
                rs.getString("name"),
                rs.getDouble("hearts"),
                rs.getDouble("max_hearts"),
                rs.getInt("kills"),
                rs.getInt("deaths"),
                rs.getInt("revives"),
                rs.getInt("times_revived"),
                rs.getInt("eliminations"),
                rs.getInt("kill_streak"),
                rs.getInt("best_kill_streak"),
                rs.getDouble("hearts_gained"),
                rs.getDouble("hearts_lost"),
                rs.getInt("eliminated") != 0,
                rs.getLong("eliminated_at"),
                rs.getDouble("hearts_before_elimination"),
                rs.getString("elimination_cause"),
                rs.getString("eliminated_by"),
                rs.getLong("ban_expires_at"),
                rs.getInt("pending_revive") != 0,
                rs.getLong("revived_at"),
                rs.getString("revived_by"),
                rs.getLong("last_death_at"),
                rs.getString("last_death_cause"),
                rs.getString("last_killer"),
                rs.getLong("first_join"),
                rs.getLong("last_seen"),
                0L);
    }

    protected static String playerPlaceholders() {
        StringBuilder builder = new StringBuilder();
        int count = PLAYER_COLUMNS.split(",").length;
        for (int i = 0; i < count; i++) {
            builder.append(i == 0 ? "?" : ", ?");
        }
        return builder.toString();
    }

    protected static UUID parseUuid(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
