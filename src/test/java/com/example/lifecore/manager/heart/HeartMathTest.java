package com.example.lifecore.manager.heart;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeartMathTest {

    @Test
    void sanitizeRejectsNaNInfinityAndNegatives() {
        assertEquals(0, HeartMath.sanitize(Double.NaN));
        assertEquals(0, HeartMath.sanitize(Double.POSITIVE_INFINITY));
        assertEquals(0, HeartMath.sanitize(Double.NEGATIVE_INFINITY));
        assertEquals(0, HeartMath.sanitize(-3));
        assertEquals(4.5, HeartMath.sanitize(4.5));
    }

    @Test
    void roundsToHalfOrWholeHearts() {
        assertEquals(1.5, HeartMath.round(1.4, true));
        assertEquals(1.0, HeartMath.round(1.2, true));
        assertEquals(1.0, HeartMath.round(1.4, false));
        assertEquals(2.0, HeartMath.round(1.5, false));
        assertEquals(0.0, HeartMath.round(Double.NaN, true));
    }

    @Test
    void floorNeverRoundsUp() {
        assertEquals(1.5, HeartMath.floor(1.99, true));
        assertEquals(1.0, HeartMath.floor(1.99, false));
    }

    @Test
    void parsesValidAmounts() {
        assertEquals(OptionalDouble.of(2.0), HeartMath.parse("2", 100, false, true));
        assertEquals(OptionalDouble.of(1.5), HeartMath.parse("1.5", 100, false, true));
        assertEquals(OptionalDouble.of(1.5), HeartMath.parse("1,5", 100, false, true));
        assertEquals(OptionalDouble.of(0.0), HeartMath.parse("0", 100, true, true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "-1", "NaN", "Infinity", "1e5", "0x10", "abc", "1.25", "101", "99999999999999", "1..5", " "})
    void rejectsInvalidAmounts(String input) {
        assertTrue(HeartMath.parse(input, 100, false, true).isEmpty(), "should reject '" + input + "'");
    }

    @Test
    void rejectsZeroWhenNotAllowedAndHalvesWhenDisabled() {
        assertTrue(HeartMath.parse("0", 100, false, true).isEmpty());
        assertTrue(HeartMath.parse("1.5", 100, false, false).isEmpty());
    }

    @Test
    void convertsToHealthPointsWithinMinecraftLimits() {
        assertEquals(20.0, HeartMath.toHealthPoints(10));
        assertEquals(1.0, HeartMath.toHealthPoints(0));
        assertEquals(1024.0, HeartMath.toHealthPoints(100_000));
        assertEquals(1.0, HeartMath.toHealthPoints(Double.NaN));
    }

    @Test
    void stepDetection() {
        assertTrue(HeartMath.isStep(2.5, true));
        assertFalse(HeartMath.isStep(2.5, false));
        assertFalse(HeartMath.isStep(2.25, true));
    }
}
