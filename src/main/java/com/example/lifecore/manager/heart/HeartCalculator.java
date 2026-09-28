package com.example.lifecore.manager.heart;

/**
 * Pure heart arithmetic shared by every heart-changing feature (and covered by unit tests).
 */
public final class HeartCalculator {

    private HeartCalculator() {
    }

    /**
     * @param newHearts value after the loss
     * @param applied   hearts actually removed
     * @param eliminate true if the player must be eliminated
     */
    public record LossOutcome(double newHearts, double applied, boolean eliminate) {
    }

    /**
     * @param newHearts value after the gain
     * @param applied   hearts actually added
     * @param overflow  hearts that did not fit under the cap
     */
    public record GainOutcome(double newHearts, double applied, double overflow) {
    }

    /**
     * Computes a heart loss.
     * <p>
     * If elimination is enabled and the result would be at or below the threshold, the player
     * is eliminated. Otherwise the result never goes below the minimum (and a loss never raises
     * a player that is already below the minimum).
     */
    public static LossOutcome loss(double current, double amount, double minimum, boolean eliminationEnabled, double threshold) {
        double cur = HeartMath.sanitize(current);
        double amt = HeartMath.sanitize(amount);
        if (amt <= 0) {
            return new LossOutcome(cur, 0, false);
        }
        double raw = cur - amt;
        if (eliminationEnabled && HeartMath.lessOrEqual(raw, threshold)) {
            return new LossOutcome(0, cur, true);
        }
        double floor = Math.min(cur, minimum);
        double result = Math.max(raw, floor);
        return new LossOutcome(result, cur - result, false);
    }

    /**
     * Computes a heart gain limited by the cap. A gain never lowers a player that is above the cap.
     */
    public static GainOutcome gain(double current, double amount, double cap) {
        double cur = HeartMath.sanitize(current);
        double amt = HeartMath.sanitize(amount);
        if (amt <= 0) {
            return new GainOutcome(cur, 0, 0);
        }
        double ceiling = Math.max(cur, cap);
        double result = Math.min(cur + amt, ceiling);
        double applied = result - cur;
        return new GainOutcome(result, applied, Math.max(0, amt - applied));
    }

    /** Clamps an absolute value between minimum and cap. */
    public static double set(double value, double minimum, double cap) {
        return HeartMath.clamp(value, Math.min(minimum, cap), cap);
    }
}
