package com.example.lifecore.api;

/**
 * High level life status of a player.
 */
public enum PlayerStatus {
    /** Playing normally. */
    ALIVE,
    /** Eliminated and waiting for revival (spectating or locked out). */
    ELIMINATED,
    /** Eliminated with an active temporary (or explicitly configured permanent) ban. */
    BANNED
}
