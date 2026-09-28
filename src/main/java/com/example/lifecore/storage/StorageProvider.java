package com.example.lifecore.storage;

import com.example.lifecore.api.LeaderboardEntry;
import com.example.lifecore.api.LeaderboardType;
import com.example.lifecore.model.BeaconRecord;
import com.example.lifecore.model.EliminatedPlayer;
import com.example.lifecore.model.PlayerSnapshot;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Blocking persistence API. Implementations are only ever called from the single database
 * thread owned by {@link com.example.lifecore.database.Database}, never from a server thread.
 */
public interface StorageProvider {

    void init() throws StorageException;

    String describe();

    Optional<PlayerSnapshot> loadPlayer(UUID uuid) throws StorageException;

    Optional<PlayerSnapshot> loadPlayerByName(String name) throws StorageException;

    void savePlayer(PlayerSnapshot snapshot) throws StorageException;

    void savePlayers(Collection<PlayerSnapshot> snapshots) throws StorageException;

    boolean deletePlayer(UUID uuid) throws StorageException;

    List<LeaderboardEntry> top(LeaderboardType type, int limit, boolean excludeEliminated) throws StorageException;

    List<EliminatedPlayer> listEliminated(int limit) throws StorageException;

    List<String> findNames(String prefix, int limit) throws StorageException;

    int countPlayers() throws StorageException;

    void registerItem(UUID id, String type, double value, UUID issuer, long createdAt) throws StorageException;

    ConsumeResult consumeItem(UUID id, String type, double value, UUID consumer, long time) throws StorageException;

    List<BeaconRecord> loadBeacons() throws StorageException;

    void saveBeacon(BeaconRecord record) throws StorageException;

    void deleteBeacon(UUID id) throws StorageException;

    /** Lightweight connectivity check. */
    boolean ping();

    void close();
}
