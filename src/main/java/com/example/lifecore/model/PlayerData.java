package com.example.lifecore.model;

import com.example.lifecore.api.LifePlayerData;
import com.example.lifecore.api.PlayerStatus;

import java.util.UUID;

/**
 * Mutable, thread-safe player data held in the cache.
 * <p>
 * All accessors are synchronized: on Folia a killer and victim may be ticked by different
 * region threads, and admin commands may run on the global thread. Every mutation bumps the
 * revision counter so the persistence layer knows whether a snapshot is still current.
 * Values are sanitised here as a last line of defence (no NaN, no negatives, no overflow).
 */
public final class PlayerData implements LifePlayerData {

    private final UUID uuid;
    private String name;
    private double hearts;
    private double maxHeartsOverride = -1;
    private int kills;
    private int deaths;
    private int revives;
    private int timesRevived;
    private int eliminations;
    private int killStreak;
    private int bestKillStreak;
    private double heartsGained;
    private double heartsLost;
    private boolean eliminated;
    private long eliminatedAt;
    private double heartsBeforeElimination;
    private String eliminationCause = "";
    private String eliminatedBy = "";
    private long banExpiresAt;
    private boolean pendingRevive;
    private long revivedAt;
    private String revivedBy = "";
    private long lastDeathAt;
    private String lastDeathCause = "";
    private String lastKiller = "";
    private long firstJoin;
    private long lastSeen;

    private long revision;
    private long savedRevision = -1;

    public PlayerData(UUID uuid, String name, double startingHearts) {
        this.uuid = uuid;
        this.name = name == null ? "" : name;
        this.hearts = sanitize(startingHearts);
        long now = System.currentTimeMillis();
        this.firstJoin = now;
        this.lastSeen = now;
        this.revision = 1;
    }

    private PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public static PlayerData fromSnapshot(PlayerSnapshot s) {
        PlayerData data = new PlayerData(s.uuid());
        data.name = s.name() == null ? "" : s.name();
        data.hearts = sanitize(s.hearts());
        data.maxHeartsOverride = Double.isFinite(s.maxHeartsOverride()) ? s.maxHeartsOverride() : -1;
        data.kills = Math.max(0, s.kills());
        data.deaths = Math.max(0, s.deaths());
        data.revives = Math.max(0, s.revives());
        data.timesRevived = Math.max(0, s.timesRevived());
        data.eliminations = Math.max(0, s.eliminations());
        data.killStreak = Math.max(0, s.killStreak());
        data.bestKillStreak = Math.max(0, s.bestKillStreak());
        data.heartsGained = sanitize(s.heartsGained());
        data.heartsLost = sanitize(s.heartsLost());
        data.eliminated = s.eliminated();
        data.eliminatedAt = Math.max(0, s.eliminatedAt());
        data.heartsBeforeElimination = sanitize(s.heartsBeforeElimination());
        data.eliminationCause = nonNull(s.eliminationCause());
        data.eliminatedBy = nonNull(s.eliminatedBy());
        data.banExpiresAt = s.banExpiresAt() < -1 ? 0 : s.banExpiresAt();
        data.pendingRevive = s.pendingRevive();
        data.revivedAt = Math.max(0, s.revivedAt());
        data.revivedBy = nonNull(s.revivedBy());
        data.lastDeathAt = Math.max(0, s.lastDeathAt());
        data.lastDeathCause = nonNull(s.lastDeathCause());
        data.lastKiller = nonNull(s.lastKiller());
        data.firstJoin = Math.max(0, s.firstJoin());
        data.lastSeen = Math.max(0, s.lastSeen());
        data.revision = 1;
        data.savedRevision = 1;
        return data;
    }

    public synchronized PlayerSnapshot snapshot() {
        return new PlayerSnapshot(uuid, name, hearts, maxHeartsOverride, kills, deaths, revives, timesRevived,
                eliminations, killStreak, bestKillStreak, heartsGained, heartsLost, eliminated, eliminatedAt,
                heartsBeforeElimination, eliminationCause, eliminatedBy, banExpiresAt, pendingRevive, revivedAt,
                revivedBy, lastDeathAt, lastDeathCause, lastKiller, firstJoin, lastSeen, revision);
    }

    // ------------------------------------------------------------------ persistence state

    public synchronized boolean isDirty() {
        return revision != savedRevision;
    }

    /** Marks the given revision as persisted. Later modifications keep the data dirty. */
    public synchronized void markSaved(long savedRevision) {
        if (savedRevision > this.savedRevision) {
            this.savedRevision = savedRevision;
        }
    }

    public synchronized long getRevision() {
        return revision;
    }

    private void touch() {
        revision++;
    }

    // ------------------------------------------------------------------ getters

    @Override
    public UUID getUniqueId() {
        return uuid;
    }

    @Override
    public synchronized String getName() {
        return name;
    }

    @Override
    public synchronized double getHearts() {
        return hearts;
    }

    @Override
    public synchronized double getMaxHeartsOverride() {
        return maxHeartsOverride;
    }

    @Override
    public synchronized int getKills() {
        return kills;
    }

    @Override
    public synchronized int getDeaths() {
        return deaths;
    }

    @Override
    public synchronized int getRevives() {
        return revives;
    }

    @Override
    public synchronized int getTimesRevived() {
        return timesRevived;
    }

    @Override
    public synchronized int getEliminations() {
        return eliminations;
    }

    @Override
    public synchronized int getKillStreak() {
        return killStreak;
    }

    @Override
    public synchronized int getBestKillStreak() {
        return bestKillStreak;
    }

    @Override
    public synchronized double getHeartsGained() {
        return heartsGained;
    }

    @Override
    public synchronized double getHeartsLost() {
        return heartsLost;
    }

    @Override
    public synchronized boolean isEliminated() {
        return eliminated;
    }

    @Override
    public synchronized long getEliminatedAt() {
        return eliminatedAt;
    }

    @Override
    public synchronized double getHeartsBeforeElimination() {
        return heartsBeforeElimination;
    }

    @Override
    public synchronized String getEliminationCause() {
        return eliminationCause;
    }

    public synchronized String getEliminatedBy() {
        return eliminatedBy;
    }

    @Override
    public synchronized long getBanExpiresAt() {
        return banExpiresAt;
    }

    @Override
    public synchronized boolean isBanned() {
        return isBannedAt(System.currentTimeMillis());
    }

    public synchronized boolean isBannedAt(long now) {
        return eliminated && (banExpiresAt == -1 || banExpiresAt > now);
    }

    /** @return remaining ban time in millis, -1 if permanent, 0 if not banned. */
    public synchronized long getRemainingBanMillis(long now) {
        if (!eliminated || banExpiresAt == 0) {
            return 0;
        }
        if (banExpiresAt == -1) {
            return -1;
        }
        return Math.max(0, banExpiresAt - now);
    }

    public synchronized boolean isPendingRevive() {
        return pendingRevive;
    }

    @Override
    public synchronized long getRevivedAt() {
        return revivedAt;
    }

    @Override
    public synchronized String getRevivedBy() {
        return revivedBy;
    }

    @Override
    public synchronized long getLastDeathAt() {
        return lastDeathAt;
    }

    @Override
    public synchronized String getLastDeathCause() {
        return lastDeathCause;
    }

    @Override
    public synchronized String getLastKiller() {
        return lastKiller;
    }

    @Override
    public synchronized long getFirstJoin() {
        return firstJoin;
    }

    @Override
    public synchronized long getLastSeen() {
        return lastSeen;
    }

    @Override
    public synchronized PlayerStatus getStatus() {
        if (!eliminated) {
            return PlayerStatus.ALIVE;
        }
        return isBannedAt(System.currentTimeMillis()) ? PlayerStatus.BANNED : PlayerStatus.ELIMINATED;
    }

    // ------------------------------------------------------------------ mutations

    public synchronized void setName(String name) {
        if (name != null && !name.equals(this.name)) {
            this.name = name;
            touch();
        }
    }

    /**
     * Raw heart setter used by HeartManager after all rules were applied. Tracks gained/lost totals.
     */
    public synchronized void setHeartsInternal(double value) {
        double sanitized = sanitize(value);
        double delta = sanitized - hearts;
        if (delta > 0) {
            heartsGained = sanitize(heartsGained + delta);
        } else if (delta < 0) {
            heartsLost = sanitize(heartsLost - delta);
        }
        hearts = sanitized;
        touch();
    }

    public synchronized void setMaxHeartsOverride(double value) {
        this.maxHeartsOverride = Double.isFinite(value) && value > 0 ? value : -1;
        touch();
    }

    public synchronized void addKill() {
        kills = saturatingIncrement(kills);
        killStreak = saturatingIncrement(killStreak);
        if (killStreak > bestKillStreak) {
            bestKillStreak = killStreak;
        }
        touch();
    }

    public synchronized void recordDeath(String cause, String killer, long time) {
        deaths = saturatingIncrement(deaths);
        killStreak = 0;
        lastDeathAt = time;
        lastDeathCause = nonNull(cause);
        lastKiller = nonNull(killer);
        touch();
    }

    public synchronized void addRevivePerformed() {
        revives = saturatingIncrement(revives);
        touch();
    }

    /**
     * Marks the player eliminated. Returns false if the player was already eliminated,
     * which guarantees an elimination can never be applied twice.
     */
    public synchronized boolean eliminate(long time, String cause, String by, long banExpiresAt) {
        if (eliminated) {
            return false;
        }
        heartsBeforeElimination = hearts;
        eliminated = true;
        eliminatedAt = time;
        eliminationCause = nonNull(cause);
        eliminatedBy = nonNull(by);
        this.banExpiresAt = banExpiresAt < -1 ? 0 : banExpiresAt;
        eliminations = saturatingIncrement(eliminations);
        pendingRevive = false;
        hearts = 0;
        killStreak = 0;
        touch();
        return true;
    }

    /**
     * Revives the player. Returns false if the player was not eliminated.
     *
     * @param online true if the player is online and the revive effects are applied immediately
     */
    public synchronized boolean revive(double restoredHearts, long time, String by, boolean online) {
        if (!eliminated) {
            return false;
        }
        eliminated = false;
        banExpiresAt = 0;
        hearts = sanitize(restoredHearts);
        revivedAt = time;
        revivedBy = nonNull(by);
        timesRevived = saturatingIncrement(timesRevived);
        pendingRevive = !online;
        touch();
        return true;
    }

    public synchronized void clearPendingRevive() {
        if (pendingRevive) {
            pendingRevive = false;
            touch();
        }
    }

    public synchronized void setBanExpiresAt(long banExpiresAt) {
        this.banExpiresAt = banExpiresAt < -1 ? 0 : banExpiresAt;
        touch();
    }

    public synchronized void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
        touch();
    }

    /** Resets hearts, elimination state and optionally statistics. */
    public synchronized void reset(double startingHearts, boolean resetStatistics) {
        hearts = sanitize(startingHearts);
        eliminated = false;
        eliminatedAt = 0;
        heartsBeforeElimination = 0;
        eliminationCause = "";
        eliminatedBy = "";
        banExpiresAt = 0;
        pendingRevive = false;
        maxHeartsOverride = -1;
        if (resetStatistics) {
            kills = 0;
            deaths = 0;
            revives = 0;
            timesRevived = 0;
            eliminations = 0;
            killStreak = 0;
            bestKillStreak = 0;
            heartsGained = 0;
            heartsLost = 0;
            lastDeathAt = 0;
            lastDeathCause = "";
            lastKiller = "";
            revivedAt = 0;
            revivedBy = "";
        }
        touch();
    }

    // ------------------------------------------------------------------ helpers

    static double sanitize(double value) {
        if (!Double.isFinite(value) || value < 0) {
            return 0;
        }
        return Math.min(value, 1_000_000_000d);
    }

    static int saturatingIncrement(int value) {
        return value == Integer.MAX_VALUE ? value : value + 1;
    }

    private static String nonNull(String value) {
        return value == null ? "" : value;
    }

    @Override
    public String toString() {
        return "PlayerData{" + uuid + ", " + getName() + ", hearts=" + getHearts() + ", eliminated=" + isEliminated() + '}';
    }
}
