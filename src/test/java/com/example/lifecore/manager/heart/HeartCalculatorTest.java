package com.example.lifecore.manager.heart;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeartCalculatorTest {

    @Test
    void gainIsLimitedByCapAndReportsOverflow() {
        HeartCalculator.GainOutcome outcome = HeartCalculator.gain(19, 3, 20);
        assertEquals(20, outcome.newHearts());
        assertEquals(1, outcome.applied());
        assertEquals(2, outcome.overflow());
    }

    @Test
    void gainAtMaximumAddsNothing() {
        HeartCalculator.GainOutcome outcome = HeartCalculator.gain(20, 1, 20);
        assertEquals(20, outcome.newHearts());
        assertEquals(0, outcome.applied());
        assertEquals(1, outcome.overflow());
    }

    @Test
    void gainNeverLowersPlayerAboveCap() {
        HeartCalculator.GainOutcome outcome = HeartCalculator.gain(25, 1, 20);
        assertEquals(25, outcome.newHearts());
        assertEquals(0, outcome.applied());
    }

    @Test
    void lossRespectsMinimumWhenEliminationDisabled() {
        HeartCalculator.LossOutcome outcome = HeartCalculator.loss(2, 5, 1, false, 0);
        assertEquals(1, outcome.newHearts());
        assertEquals(1, outcome.applied());
        assertFalse(outcome.eliminate());
    }

    @Test
    void lossToThresholdEliminates() {
        HeartCalculator.LossOutcome outcome = HeartCalculator.loss(1, 1, 1, true, 0);
        assertTrue(outcome.eliminate());
        assertEquals(0, outcome.newHearts());
        assertEquals(1, outcome.applied());
    }

    @Test
    void lossAboveThresholdButBelowMinimumClampsToMinimum() {
        HeartCalculator.LossOutcome outcome = HeartCalculator.loss(1.5, 1, 1, true, 0);
        assertFalse(outcome.eliminate());
        assertEquals(1, outcome.newHearts());
        assertEquals(0.5, outcome.applied());
    }

    @Test
    void lossNeverRaisesPlayerBelowMinimum() {
        HeartCalculator.LossOutcome outcome = HeartCalculator.loss(0.5, 1, 1, false, 0);
        assertEquals(0.5, outcome.newHearts());
        assertEquals(0, outcome.applied());
    }

    @Test
    void negativeOrNaNAmountsChangeNothing() {
        assertEquals(10, HeartCalculator.loss(10, -5, 1, true, 0).newHearts());
        assertEquals(10, HeartCalculator.gain(10, Double.NaN, 20).newHearts());
    }

    @Test
    void customThresholdEliminatesEarlier() {
        assertTrue(HeartCalculator.loss(3, 1, 1, true, 2).eliminate());
        assertFalse(HeartCalculator.loss(4, 1, 1, true, 2).eliminate());
    }

    @Test
    void setClampsBetweenMinimumAndCap() {
        assertEquals(20, HeartCalculator.set(50, 1, 20));
        assertEquals(1, HeartCalculator.set(0, 1, 20));
        assertEquals(7.5, HeartCalculator.set(7.5, 1, 20));
    }
}
