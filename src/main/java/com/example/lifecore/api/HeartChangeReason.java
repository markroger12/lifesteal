package com.example.lifecore.api;

/**
 * Why a heart value changed. Passed to heart events so listeners can react selectively.
 */
public enum HeartChangeReason {
    KILL_REWARD,
    KILL_LOSS,
    DEATH,
    HEART_ITEM,
    SACRIFICE,
    WITHDRAW,
    REDEEM,
    REVIVE,
    REVIVE_COST,
    ADMIN,
    RESET,
    ELIMINATION,
    CAP_ENFORCEMENT,
    API;

    public boolean isGain() {
        return this == KILL_REWARD || this == HEART_ITEM || this == REDEEM || this == REVIVE;
    }
}
