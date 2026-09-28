package com.example.lifecore.manager.heart;

import java.math.BigDecimal;
import java.util.OptionalDouble;

/**
 * Pure numeric helpers for heart values. Every externally supplied number goes through here,
 * so NaN, Infinity, negative values and absurdly large numbers can never reach player data.
 */
public final class HeartMath {

    /** Minecraft's max-health attribute is limited to 1024 health points. */
    public static final double MAX_HEALTH_POINTS = 1024.0;
    private static final double EPSILON = 1e-9;

    private HeartMath() {
    }

    public static double sanitize(double value) {
        if (!Double.isFinite(value) || value < 0) {
            return 0;
        }
        return value;
    }

    public static double clamp(double value, double min, double max) {
        double v = sanitize(value);
        if (v < min) {
            return min;
        }
        return Math.min(v, max);
    }

    /** Rounds to the nearest half heart (or whole heart when half hearts are disabled). */
    public static double round(double value, boolean halfHearts) {
        double v = sanitize(value);
        if (halfHearts) {
            return Math.round(v * 2.0) / 2.0;
        }
        return Math.round(v);
    }

    /** Rounds down to the nearest representable step (used where rounding up would create hearts). */
    public static double floor(double value, boolean halfHearts) {
        double v = sanitize(value);
        if (halfHearts) {
            return Math.floor(v * 2.0 + EPSILON) / 2.0;
        }
        return Math.floor(v + EPSILON);
    }

    public static boolean isStep(double value, boolean halfHearts) {
        double scaled = halfHearts ? value * 2.0 : value;
        return Math.abs(scaled - Math.rint(scaled)) < EPSILON;
    }

    /**
     * Parses a user supplied heart amount.
     *
     * @param input      raw text
     * @param max        largest accepted value
     * @param allowZero  whether 0 is accepted
     * @param halfHearts whether .5 values are accepted
     * @return the value or empty if invalid
     */
    public static OptionalDouble parse(String input, double max, boolean allowZero, boolean halfHearts) {
        if (input == null) {
            return OptionalDouble.empty();
        }
        String text = input.trim().replace(',', '.');
        if (text.isEmpty() || text.length() > 12) {
            return OptionalDouble.empty();
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!(Character.isDigit(c) || c == '.')) {
                return OptionalDouble.empty();
            }
        }
        double value;
        try {
            value = new BigDecimal(text).doubleValue();
        } catch (NumberFormatException ex) {
            return OptionalDouble.empty();
        }
        if (!Double.isFinite(value) || value < 0 || value > max) {
            return OptionalDouble.empty();
        }
        if (!allowZero && value <= 0) {
            return OptionalDouble.empty();
        }
        if (!isStep(value, halfHearts)) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(value);
    }

    /** Converts hearts into a max-health attribute value (at least half a heart). */
    public static double toHealthPoints(double hearts) {
        return Math.max(1.0, Math.min(MAX_HEALTH_POINTS, sanitize(hearts) * 2.0));
    }

    public static boolean lessOrEqual(double a, double b) {
        return a <= b + EPSILON;
    }

    public static boolean greaterOrEqual(double a, double b) {
        return a + EPSILON >= b;
    }
}
