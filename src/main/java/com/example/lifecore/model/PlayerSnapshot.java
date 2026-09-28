package com.example.lifecore.model;

import java.util.UUID;

/**
 * Immutable copy of a player's data. Snapshots are taken on the thread that owns the data
 * and handed to the database thread, so persistence never races with gameplay mutations.
 */
public record PlayerSnapshot(
        UUID uuid,
        String name,
        double hearts,
        double maxHeartsOverride,
        int kills,
        int deaths,
        int revives,
        int timesRevived,
        int eliminations,
        int killStreak,
        int bestKillStreak,
        double heartsGained,
        double heartsLost,
        boolean eliminated,
        long eliminatedAt,
        double heartsBeforeElimination,
        String eliminationCause,
        String eliminatedBy,
        long banExpiresAt,
        boolean pendingRevive,
        long revivedAt,
        String revivedBy,
        long lastDeathAt,
        String lastDeathCause,
        String lastKiller,
        long firstJoin,
        long lastSeen,
        long revision
) {
}
