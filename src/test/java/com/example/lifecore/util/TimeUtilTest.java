package com.example.lifecore.util;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeUtilTest {

    @Test
    void parsesDurations() {
        assertEquals(OptionalLong.of(24L * 3600 * 1000), TimeUtil.parseDuration("24h"));
        assertEquals(OptionalLong.of(90_000), TimeUtil.parseDuration("1m30s"));
        assertEquals(OptionalLong.of((36L * 3600 + 30 * 60) * 1000), TimeUtil.parseDuration("1d12h30m"));
        assertEquals(OptionalLong.of(45_000), TimeUtil.parseDuration("45"));
        assertEquals(OptionalLong.of(7L * 24 * 3600 * 1000), TimeUtil.parseDuration("1w"));
        assertEquals(OptionalLong.of(TimeUtil.PERMANENT), TimeUtil.parseDuration("permanent"));
    }

    @Test
    void rejectsInvalidDurations() {
        assertTrue(TimeUtil.parseDuration("").isEmpty());
        assertTrue(TimeUtil.parseDuration("abc").isEmpty());
        assertTrue(TimeUtil.parseDuration("12x").isEmpty());
        assertTrue(TimeUtil.parseDuration(null).isEmpty());
    }

    @Test
    void capsHugeDurations() {
        long tenYears = 3650L * 24 * 60 * 60 * 1000;
        assertEquals(OptionalLong.of(tenYears), TimeUtil.parseDuration("999999999999w"));
    }

    @Test
    void formatsDurationsAndClocks() {
        TimeUtil.TimeUnits units = TimeUtil.TimeUnits.DEFAULT;
        assertEquals("1d 2h 3m", TimeUtil.formatDuration((26L * 3600 + 180 + 5) * 1000, units));
        assertEquals("5m 12s", TimeUtil.formatDuration(312_000, units));
        assertEquals("now", TimeUtil.formatDuration(0, units));
        assertEquals("Permanent", TimeUtil.formatDuration(-1, units));
        assertEquals("02:31", TimeUtil.formatClock(151));
        assertEquals("1:00:05", TimeUtil.formatClock(3605));
    }
}
