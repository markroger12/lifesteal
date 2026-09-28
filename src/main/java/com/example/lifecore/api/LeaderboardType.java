package com.example.lifecore.api;

import java.util.Locale;
import java.util.Optional;

/**
 * Supported leaderboards.
 */
public enum LeaderboardType {
    HEARTS("hearts"),
    KILLS("kills"),
    DEATHS("deaths"),
    REVIVES("revives");

    private final String column;

    LeaderboardType(String column) {
        this.column = column;
    }

    /** @return the database column backing this leaderboard (fixed, never user input). */
    public String column() {
        return column;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<LeaderboardType> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (LeaderboardType type : values()) {
            if (type.id().equalsIgnoreCase(id)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
