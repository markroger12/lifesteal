package com.example.lifecore.model;

import com.example.lifecore.api.PlayerStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerDataTest {

    private final UUID uuid = UUID.randomUUID();

    @Test
    void eliminationCanOnlyHappenOnce() {
        PlayerData data = new PlayerData(uuid, "Steve", 5);
        long now = System.currentTimeMillis();
        assertTrue(data.eliminate(now, "LAVA", "", now + 60_000));
        assertFalse(data.eliminate(now, "LAVA", "", now + 60_000));
        assertEquals(1, data.getEliminations());
        assertEquals(5, data.getHeartsBeforeElimination());
        assertEquals(0, data.getHearts());
        assertEquals(PlayerStatus.BANNED, data.getStatus());
    }

    @Test
    void temporaryBanExpires() {
        PlayerData data = new PlayerData(uuid, "Steve", 5);
        long now = System.currentTimeMillis();
        data.eliminate(now, "PLAYER", "Alex", now + 1_000);
        assertTrue(data.isBannedAt(now));
        assertFalse(data.isBannedAt(now + 2_000));
        assertEquals(1_000, data.getRemainingBanMillis(now));
    }

    @Test
    void permanentBanIsReported() {
        PlayerData data = new PlayerData(uuid, "Steve", 5);
        data.eliminate(System.currentTimeMillis(), "ADMIN", "Console", -1);
        assertEquals(-1, data.getRemainingBanMillis(System.currentTimeMillis()));
        assertTrue(data.isBanned());
    }

    @Test
    void reviveRestoresStateAndClearsBan() {
        PlayerData data = new PlayerData(uuid, "Steve", 5);
        long now = System.currentTimeMillis();
        data.eliminate(now, "PLAYER", "Alex", now + 60_000);
        assertTrue(data.revive(3, now, "Admin", false));
        assertFalse(data.isEliminated());
        assertEquals(3, data.getHearts());
        assertEquals(0, data.getBanExpiresAt());
        assertTrue(data.isPendingRevive());
        assertEquals(1, data.getTimesRevived());
        assertFalse(data.revive(3, now, "Admin", false), "reviving an alive player must fail");
    }

    @Test
    void snapshotRoundTripPreservesEverything() {
        PlayerData data = new PlayerData(uuid, "Steve", 12.5);
        data.addKill();
        data.recordDeath("FALL", "", 123L);
        data.setMaxHeartsOverride(30);
        PlayerData copy = PlayerData.fromSnapshot(data.snapshot());
        assertEquals(12.5, copy.getHearts());
        assertEquals(1, copy.getKills());
        assertEquals(1, copy.getDeaths());
        assertEquals("FALL", copy.getLastDeathCause());
        assertEquals(30, copy.getMaxHeartsOverride());
        assertFalse(copy.isDirty());
    }

    @Test
    void corruptedSnapshotsAreSanitised() {
        PlayerSnapshot corrupted = new PlayerSnapshot(uuid, null, Double.NaN, Double.POSITIVE_INFINITY, -5, -1, -1, -1, -1,
                -1, -1, Double.NEGATIVE_INFINITY, -2, false, -1, Double.NaN, null, null, -99, false, -1, null, -1, null,
                null, -1, -1, 0);
        PlayerData data = PlayerData.fromSnapshot(corrupted);
        assertEquals(0, data.getHearts());
        assertEquals(-1, data.getMaxHeartsOverride());
        assertEquals(0, data.getKills());
        assertEquals("", data.getName());
        assertEquals(0, data.getBanExpiresAt());
    }

    @Test
    void countersSaturateInsteadOfOverflowing() {
        PlayerSnapshot max = new PlayerSnapshot(uuid, "Steve", 10, -1, Integer.MAX_VALUE, 0, 0, 0, 0,
                Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 0, false, 0, 0, "", "", 0, false, 0, "", 0, "", "", 0, 0, 0);
        PlayerData data = PlayerData.fromSnapshot(max);
        data.addKill();
        assertEquals(Integer.MAX_VALUE, data.getKills());
    }

    @Test
    void dirtyTrackingFollowsRevisions() {
        PlayerData data = new PlayerData(uuid, "Steve", 10);
        assertTrue(data.isDirty());
        PlayerSnapshot snapshot = data.snapshot();
        data.addKill();
        data.markSaved(snapshot.revision());
        assertTrue(data.isDirty(), "a change after the snapshot must keep the data dirty");
        data.markSaved(data.snapshot().revision());
        assertFalse(data.isDirty());
    }

    @Test
    void heartTotalsAreTracked() {
        PlayerData data = new PlayerData(uuid, "Steve", 10);
        data.setHeartsInternal(12);
        data.setHeartsInternal(9);
        assertEquals(2, data.getHeartsGained());
        assertEquals(3, data.getHeartsLost());
        data.setHeartsInternal(Double.NaN);
        assertEquals(0, data.getHearts());
    }
}
