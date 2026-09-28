package com.example.lifecore.api;

/**
 * Outcome of a heart modification.
 *
 * @param previous  heart value before the change
 * @param current   heart value after the change
 * @param requested amount that was requested (absolute value)
 * @param reason    why the change happened
 * @param capped    true if the change was reduced by the minimum/maximum limits
 * @param cancelled true if an event listener or rule cancelled the change
 * @param eliminated true if the change eliminated the player
 */
public record HeartChangeResult(double previous, double current, double requested, HeartChangeReason reason,
                                boolean capped, boolean cancelled, boolean eliminated) {

    public static HeartChangeResult cancelled(double value, double requested, HeartChangeReason reason) {
        return new HeartChangeResult(value, value, requested, reason, false, true, false);
    }

    /** @return signed applied delta (positive for gains). */
    public double delta() {
        return current - previous;
    }

    public boolean changed() {
        return Double.compare(previous, current) != 0;
    }
}
