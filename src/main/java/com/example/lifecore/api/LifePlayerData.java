package com.example.lifecore.api;

import java.util.UUID;

/**
 * Read-only view of a player's LifeCore data.
 */
public interface LifePlayerData {

    UUID getUniqueId();

    String getName();

    double getHearts();

    /** @return per-player maximum heart override, or a negative value if the configured maximum applies. */
    double getMaxHeartsOverride();

    int getKills();

    int getDeaths();

    /** @return how many other players this player revived. */
    int getRevives();

    /** @return how many times this player has been revived. */
    int getTimesRevived();

    int getEliminations();

    int getKillStreak();

    int getBestKillStreak();

    double getHeartsGained();

    double getHeartsLost();

    boolean isEliminated();

    long getEliminatedAt();

    double getHeartsBeforeElimination();

    String getEliminationCause();

    /** @return epoch millis when the elimination ban ends, 0 if none, -1 if permanent. */
    long getBanExpiresAt();

    boolean isBanned();

    long getRevivedAt();

    String getRevivedBy();

    long getLastDeathAt();

    String getLastDeathCause();

    String getLastKiller();

    long getFirstJoin();

    long getLastSeen();

    PlayerStatus getStatus();
}
