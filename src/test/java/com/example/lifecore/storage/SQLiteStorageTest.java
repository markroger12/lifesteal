package com.example.lifecore.storage;

import com.example.lifecore.api.LeaderboardEntry;
import com.example.lifecore.api.LeaderboardType;
import com.example.lifecore.model.BeaconRecord;
import com.example.lifecore.model.EliminatedPlayer;
import com.example.lifecore.model.PlayerData;
import com.example.lifecore.model.PlayerSnapshot;
import com.example.lifecore.util.LifeLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLiteStorageTest {

    @TempDir
    Path folder;

    private SQLiteStorage storage;

    @BeforeEach
    void setUp() throws StorageException {
        storage = new SQLiteStorage(folder.resolve("data/test.db").toFile(), "lc_", new LifeLogger(Logger.getLogger("test")));
        storage.init();
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    private PlayerSnapshot player(String name, double hearts, int kills, boolean eliminated) {
        PlayerData data = new PlayerData(UUID.randomUUID(), name, hearts);
        for (int i = 0; i < kills; i++) {
            data.addKill();
        }
        if (eliminated) {
            data.eliminate(System.currentTimeMillis(), "LAVA", "", System.currentTimeMillis() + 60_000);
        }
        return data.snapshot();
    }

    @Test
    void savesAndLoadsPlayers() throws StorageException {
        PlayerSnapshot snapshot = player("Steve", 12.5, 3, false);
        storage.savePlayer(snapshot);
        Optional<PlayerSnapshot> loaded = storage.loadPlayer(snapshot.uuid());
        assertTrue(loaded.isPresent());
        assertEquals(12.5, loaded.get().hearts());
        assertEquals(3, loaded.get().kills());
        assertEquals("Steve", loaded.get().name());
        assertTrue(storage.loadPlayer(UUID.randomUUID()).isEmpty());
    }

    @Test
    void upsertUpdatesExistingRows() throws StorageException {
        PlayerData data = new PlayerData(UUID.randomUUID(), "Alex", 10);
        storage.savePlayer(data.snapshot());
        data.setHeartsInternal(4);
        data.eliminate(1000L, "PLAYER", "Steve", 5000L);
        storage.savePlayer(data.snapshot());
        PlayerSnapshot loaded = storage.loadPlayer(data.getUniqueId()).orElseThrow();
        assertTrue(loaded.eliminated());
        assertEquals(4, loaded.heartsBeforeElimination());
        assertEquals(5000L, loaded.banExpiresAt());
        assertEquals("Steve", loaded.eliminatedBy());
        assertEquals(1, storage.countPlayers());
    }

    @Test
    void batchSaveAndCaseInsensitiveNameLookup() throws StorageException {
        storage.savePlayers(List.of(player("Notch", 20, 0, false), player("jeb_", 15, 0, false)));
        assertEquals(2, storage.countPlayers());
        assertTrue(storage.loadPlayerByName("NOTCH").isPresent());
        assertEquals(List.of("jeb_"), storage.findNames("jeb_", 10));
        assertTrue(storage.findNames("je%", 10).isEmpty(), "LIKE wildcards in user input are escaped");
    }

    @Test
    void leaderboardsAreOrderedAndCanExcludeEliminated() throws StorageException {
        storage.savePlayers(List.of(player("A", 5, 1, false), player("B", 25, 9, false), player("C", 30, 4, true)));
        List<LeaderboardEntry> hearts = storage.top(LeaderboardType.HEARTS, 10, true);
        assertEquals(List.of("B", "A"), hearts.stream().map(LeaderboardEntry::name).toList());
        assertEquals(1, hearts.get(0).position());
        List<LeaderboardEntry> kills = storage.top(LeaderboardType.KILLS, 2, false);
        assertEquals(List.of("B", "C"), kills.stream().map(LeaderboardEntry::name).toList());
    }

    @Test
    void listsEliminatedPlayers() throws StorageException {
        storage.savePlayers(List.of(player("Alive", 10, 0, false), player("Dead", 10, 0, true)));
        List<EliminatedPlayer> eliminated = storage.listEliminated(10);
        assertEquals(1, eliminated.size());
        assertEquals("Dead", eliminated.get(0).name());
        assertEquals("LAVA", eliminated.get(0).cause());
    }

    @Test
    void itemLedgerAllowsExactlyOneRedemption() throws StorageException {
        UUID note = UUID.randomUUID();
        UUID issuer = UUID.randomUUID();
        storage.registerItem(note, "note", 3.0, issuer, 1L);
        assertEquals(ConsumeResult.CONSUMED, storage.consumeItem(note, "note", 3.0, issuer, 2L));
        assertEquals(ConsumeResult.ALREADY_CONSUMED, storage.consumeItem(note, "note", 3.0, UUID.randomUUID(), 3L));
    }

    @Test
    void unregisteredButSignedItemsCanBeConsumedOnce() throws StorageException {
        UUID note = UUID.randomUUID();
        assertEquals(ConsumeResult.CONSUMED, storage.consumeItem(note, "note", 2.0, UUID.randomUUID(), 5L));
        assertEquals(ConsumeResult.ALREADY_CONSUMED, storage.consumeItem(note, "note", 2.0, UUID.randomUUID(), 6L));
    }

    @Test
    void beaconsPersistAndDelete() throws StorageException {
        BeaconRecord record = new BeaconRecord(UUID.randomUUID(), "basic", "world", 10, 64, -20, UUID.randomUUID(), "Owner",
                UUID.randomUUID(), "Target", 120, 80, 100, 1234L, UUID.randomUUID());
        storage.saveBeacon(record);
        storage.saveBeacon(new BeaconRecord(record.id(), "basic", "world", 10, 64, -20, record.owner(), "Owner",
                record.target(), "Target", 90, 60, 100, 1234L, record.itemId()));
        List<BeaconRecord> loaded = storage.loadBeacons();
        assertEquals(1, loaded.size());
        assertEquals(90, loaded.get(0).remainingSeconds());
        assertEquals(60, loaded.get(0).durability());
        storage.deleteBeacon(record.id());
        assertTrue(storage.loadBeacons().isEmpty());
    }

    @Test
    void dataSurvivesReopeningTheDatabase() throws StorageException {
        PlayerSnapshot snapshot = player("Persistent", 17, 2, false);
        storage.savePlayer(snapshot);
        storage.close();
        storage = new SQLiteStorage(folder.resolve("data/test.db").toFile(), "lc_", new LifeLogger(Logger.getLogger("test")));
        storage.init();
        assertEquals(17, storage.loadPlayer(snapshot.uuid()).orElseThrow().hearts());
        assertTrue(storage.ping());
        assertFalse(storage.loadBeacons().iterator().hasNext());
    }
}
