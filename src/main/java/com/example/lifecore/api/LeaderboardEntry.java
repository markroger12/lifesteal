package com.example.lifecore.api;

import java.util.UUID;

/**
 * One leaderboard row.
 */
public record LeaderboardEntry(int position, UUID uuid, String name, double value) {
}
