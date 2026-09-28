package com.example.lifecore.util;

import java.util.Locale;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Duration parsing and formatting.
 * <p>
 * Accepted input: {@code 30s}, {@code 15m}, {@code 24h}, {@code 7d}, {@code 2w}, combinations such
 * as {@code 1d12h30m}, plain numbers (seconds) and {@code permanent}/{@code perm}/{@code forever}/{@code -1}.
 */
public final class TimeUtil {

    public static final long PERMANENT = -1L;

    private static final Pattern PART = Pattern.compile("(\\d+)\\s*(w|d|h|m|s)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FULL = Pattern.compile("^(\\s*\\d+\\s*[wdhms]\\s*)+$", Pattern.CASE_INSENSITIVE);
    private static final long MAX_MILLIS = 3650L * 24 * 60 * 60 * 1000; // ten years

    private TimeUtil() {
    }

    /**
     * Parses a duration.
     *
     * @return milliseconds, {@link #PERMANENT} for permanent, or empty if the input is invalid
     */
    public static OptionalLong parseDuration(String input) {
        if (input == null) {
            return OptionalLong.empty();
        }
        String value = input.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return OptionalLong.empty();
        }
        if (value.equals("permanent") || value.equals("perm") || value.equals("forever") || value.equals("-1")) {
            return OptionalLong.of(PERMANENT);
        }
        if (value.chars().allMatch(Character::isDigit)) {
            try {
                long seconds = Long.parseLong(value);
                return OptionalLong.of(Math.min(MAX_MILLIS, seconds * 1000L));
            } catch (NumberFormatException ex) {
                return OptionalLong.empty();
            }
        }
        if (!FULL.matcher(value).matches()) {
            return OptionalLong.empty();
        }
        Matcher matcher = PART.matcher(value);
        long total = 0;
        while (matcher.find()) {
            long amount;
            try {
                amount = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException ex) {
                return OptionalLong.empty();
            }
            long unit = switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
                case "w" -> 7L * 24 * 60 * 60 * 1000;
                case "d" -> 24L * 60 * 60 * 1000;
                case "h" -> 60L * 60 * 1000;
                case "m" -> 60L * 1000;
                default -> 1000L;
            };
            if (amount > MAX_MILLIS / unit) {
                return OptionalLong.of(MAX_MILLIS);
            }
            total += amount * unit;
            if (total > MAX_MILLIS) {
                return OptionalLong.of(MAX_MILLIS);
            }
        }
        return OptionalLong.of(total);
    }

    /**
     * Formats milliseconds as a compact duration, e.g. {@code 1d 4h 3m}.
     */
    public static String formatDuration(long millis, TimeUnits units) {
        if (millis < 0) {
            return units.permanent();
        }
        long seconds = millis / 1000L;
        if (seconds <= 0) {
            return units.now();
        }
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;
        StringBuilder builder = new StringBuilder();
        int parts = 0;
        if (days > 0) {
            builder.append(days).append(units.days());
            parts++;
        }
        if (hours > 0) {
            appendSpace(builder).append(hours).append(units.hours());
            parts++;
        }
        if (minutes > 0 && parts < 3) {
            appendSpace(builder).append(minutes).append(units.minutes());
            parts++;
        }
        if (seconds > 0 && parts < 2 && days == 0) {
            appendSpace(builder).append(seconds).append(units.seconds());
        }
        return builder.length() == 0 ? units.now() : builder.toString();
    }

    /** Formats seconds as {@code mm:ss} or {@code h:mm:ss}. */
    public static String formatClock(long totalSeconds) {
        long s = Math.max(0, totalSeconds);
        long hours = s / 3600;
        long minutes = (s % 3600) / 60;
        long seconds = s % 60;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    private static StringBuilder appendSpace(StringBuilder builder) {
        if (builder.length() > 0) {
            builder.append(' ');
        }
        return builder;
    }

    /**
     * Configurable unit suffixes (from messages.yml).
     */
    public record TimeUnits(String days, String hours, String minutes, String seconds, String permanent, String now) {
        public static final TimeUnits DEFAULT = new TimeUnits("d", "h", "m", "s", "Permanent", "now");
    }
}
