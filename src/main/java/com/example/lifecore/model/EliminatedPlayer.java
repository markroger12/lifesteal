package com.example.lifecore.model;

import java.util.UUID;

/**
 * Lightweight row describing an eliminated player (used by revive menus and beacons).
 */
public record EliminatedPlayer(UUID uuid, String name, long eliminatedAt, long banExpiresAt, String cause) {
}
