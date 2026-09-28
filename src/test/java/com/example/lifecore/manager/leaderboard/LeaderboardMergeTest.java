package com.example.lifecore.manager.leaderboard;

import com.example.lifecore.api.LeaderboardEntry;
import com.example.lifecore.api.LeaderboardType;
import com.example.lifecore.model.PlayerData;
import com.example.lifecore.model.PlayerSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LeaderboardMergeTest {

    @Test
    void onlineValuesOverrideStaleDatabaseRows() {
        UUID steve = UUID.randomUUID();
        UUID alex = UUID.randomUUID();
        List<LeaderboardEntry> database = List.of(
                new LeaderboardEntry(1, steve, "Steve", 20),
                new LeaderboardEntry(2, alex, "Alex", 15));
        PlayerSnapshot alexOnline = new PlayerData(alex, "Alex", 25).snapshot();
        List<LeaderboardEntry> merged = LeaderboardManager.merge(LeaderboardType.HEARTS, database, List.of(alexOnline), 10, true);
        assertEquals("Alex", merged.get(0).name());
        assertEquals(25, merged.get(0).value());
        assertEquals(1, merged.get(0).position());
        assertEquals(2, merged.get(1).position());
    }

    @Test
    void eliminatedOnlinePlayersAreRemovedFromHearts() {
        UUID dead = UUID.randomUUID();
        PlayerData data = new PlayerData(dead, "Dead", 30);
        data.eliminate(1L, "LAVA", "", 0);
        List<LeaderboardEntry> merged = LeaderboardManager.merge(LeaderboardType.HEARTS,
                List.of(new LeaderboardEntry(1, dead, "Dead", 30)), List.of(data.snapshot()), 10, true);
        assertEquals(0, merged.size());
    }

    @Test
    void sizeLimitIsApplied() {
        List<PlayerSnapshot> online = List.of(
                new PlayerData(UUID.randomUUID(), "A", 1).snapshot(),
                new PlayerData(UUID.randomUUID(), "B", 2).snapshot(),
                new PlayerData(UUID.randomUUID(), "C", 3).snapshot());
        List<LeaderboardEntry> merged = LeaderboardManager.merge(LeaderboardType.HEARTS, List.of(), online, 2, false);
        assertEquals(List.of("C", "B"), merged.stream().map(LeaderboardEntry::name).toList());
    }
}
