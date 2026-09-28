package com.example.lifecore.api;

/**
 * Result of a revive attempt.
 */
public enum ReviveResult {
    SUCCESS,
    NOT_FOUND,
    NOT_ELIMINATED,
    CANCELLED,
    INSUFFICIENT_HEARTS,
    INSUFFICIENT_FUNDS,
    ON_COOLDOWN,
    DISABLED,
    ALREADY_IN_PROGRESS,
    ERROR;

    public boolean isSuccess() {
        return this == SUCCESS;
    }
}
