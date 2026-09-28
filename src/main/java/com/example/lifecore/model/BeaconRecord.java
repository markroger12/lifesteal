package com.example.lifecore.model;

import java.util.UUID;

/**
 * Persisted state of an active revive beacon.
 */
public record BeaconRecord(
        UUID id,
        String tier,
        String world,
        int x,
        int y,
        int z,
        UUID owner,
        String ownerName,
        UUID target,
        String targetName,
        int remainingSeconds,
        double durability,
        double maxDurability,
        long startedAt,
        UUID itemId
) {
}
