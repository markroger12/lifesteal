package com.example.lifecore.manager.player;

import com.example.lifecore.database.Database;
import com.example.lifecore.model.PlayerData;
import com.example.lifecore.model.PlayerSnapshot;
import com.example.lifecore.storage.StorageException;
import com.example.lifecore.util.LifeLogger;
import com.example.lifecore.util.scheduler.TaskScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Owns the player data cache.
 * <ul>
 *     <li>Data is loaded on the async pre-login thread (the only place blocking waits are allowed).</li>
 *     <li>Saves always persist an immutable snapshot on the database thread.</li>
 *     <li>Offline edits are registered as pending so a login for the same player waits for them,
 *     which rules out lost updates between an offline command and a simultaneous join.</li>
 * </ul>
 */
public final class PlayerDataManager {

    private static final Pattern NAME = Pattern.compile("^[.*]?[A-Za-z0-9_]{1,16}$");

    private final Database database;
    private final TaskScheduler scheduler;
    private final LifeLogger logger;
    private final DoubleSupplier startingHearts;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> loadedAt = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<?>> pendingEdits = new ConcurrentHashMap<>();

    public PlayerDataManager(Database database, TaskScheduler scheduler, LifeLogger logger, DoubleSupplier startingHearts) {
        this.database = database;
        this.scheduler = scheduler;
        this.logger = logger;
        this.startingHearts = startingHearts;
    }

    /** Resolved player identity from a command argument. */
    public record Target(UUID uuid, String name) {
    }

    public static boolean isValidName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    // ------------------------------------------------------------------ cache access

    public @Nullable PlayerData get(UUID uuid) {
        return cache.get(uuid);
    }

    public @Nullable PlayerData get(Player player) {
        return cache.get(player.getUniqueId());
    }

    public Optional<PlayerData> find(UUID uuid) {
        return Optional.ofNullable(cache.get(uuid));
    }

    public Collection<PlayerData> cached() {
        return Collections.unmodifiableCollection(cache.values());
    }

    public boolean isCached(UUID uuid) {
        return cache.containsKey(uuid);
    }

    // ------------------------------------------------------------------ login / quit

    /**
     * Loads (or creates) a player's data. Blocking - call only from AsyncPlayerPreLoginEvent.
     */
    public PlayerData loadForLogin(UUID uuid, String name, int timeoutSeconds) throws StorageException, TimeoutException {
        CompletableFuture<?> pending = pendingEdits.get(uuid);
        if (pending != null) {
            try {
                pending.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (ExecutionException ignored) {
                // The edit failed on its own; the load below reads whatever was persisted.
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new StorageException("Interrupted while waiting for a pending edit", ex);
            }
        }
        PlayerData existing = cache.get(uuid);
        if (existing != null) {
            existing.setName(name);
            loadedAt.put(uuid, System.currentTimeMillis());
            return existing;
        }
        try {
            Optional<PlayerSnapshot> snapshot = database.submit("load " + name, s -> s.loadPlayer(uuid))
                    .get(timeoutSeconds, TimeUnit.SECONDS);
            PlayerData data;
            if (snapshot.isPresent()) {
                data = PlayerData.fromSnapshot(snapshot.get());
                data.setName(name);
            } else {
                data = new PlayerData(uuid, name, startingHearts.getAsDouble());
                logger.debug("player", "Created new player data", "player", name, "hearts", data.getHearts());
            }
            PlayerData raced = cache.putIfAbsent(uuid, data);
            loadedAt.put(uuid, System.currentTimeMillis());
            return raced != null ? raced : data;
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof StorageException storage) {
                throw storage;
            }
            throw new StorageException("Failed to load " + name + ": " + cause, cause);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new StorageException("Interrupted while loading " + name, ex);
        }
    }

    /**
     * Asynchronously loads and caches data for a player who is already online
     * (e.g. after the plugin was enabled while players were connected).
     */
    public CompletableFuture<PlayerData> loadAndCache(UUID uuid, String name) {
        PlayerData cached = cache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return database.submit("load " + name, s -> s.loadPlayer(uuid)).thenApply(snapshot -> {
            PlayerData data = snapshot.map(PlayerData::fromSnapshot)
                    .orElseGet(() -> new PlayerData(uuid, name, startingHearts.getAsDouble()));
            data.setName(name);
            PlayerData raced = cache.putIfAbsent(uuid, data);
            loadedAt.put(uuid, System.currentTimeMillis());
            return raced != null ? raced : data;
        });
    }

    /** Removes cached data for a login that was denied after loading. */
    public void discard(UUID uuid) {
        if (Bukkit.getPlayer(uuid) == null) {
            PlayerData data = cache.remove(uuid);
            loadedAt.remove(uuid);
            if (data != null && data.isDirty()) {
                save(data);
            }
        }
    }

    /**
     * Persists and evicts a player's data on quit. Eviction happens immediately; the queued save
     * runs before any later load for the same player thanks to the single database thread.
     */
    public CompletableFuture<Void> handleQuit(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data == null) {
            return CompletableFuture.completedFuture(null);
        }
        data.setLastSeen(System.currentTimeMillis());
        CompletableFuture<Void> save = save(data);
        cache.remove(uuid, data);
        loadedAt.remove(uuid);
        return save;
    }

    // ------------------------------------------------------------------ saving

    public CompletableFuture<Void> save(PlayerData data) {
        PlayerSnapshot snapshot = data.snapshot();
        return database.execute("save " + snapshot.name(), s -> s.savePlayer(snapshot))
                .thenRun(() -> data.markSaved(snapshot.revision()));
    }

    /** Saves every dirty cached player in one batch. */
    public CompletableFuture<Integer> saveAllDirty() {
        List<PlayerData> dirty = new ArrayList<>();
        List<PlayerSnapshot> snapshots = new ArrayList<>();
        for (PlayerData data : cache.values()) {
            if (data.isDirty()) {
                dirty.add(data);
                snapshots.add(data.snapshot());
            }
        }
        if (snapshots.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        return database.execute("autosave " + snapshots.size() + " players", s -> s.savePlayers(snapshots))
                .thenApply(ignored -> {
                    for (int i = 0; i < dirty.size(); i++) {
                        dirty.get(i).markSaved(snapshots.get(i).revision());
                    }
                    return snapshots.size();
                });
    }

    /** Synchronous final save used during shutdown. */
    public void saveAllBlocking(long timeoutSeconds) {
        List<PlayerSnapshot> snapshots = new ArrayList<>();
        for (PlayerData data : cache.values()) {
            data.setLastSeen(System.currentTimeMillis());
            snapshots.add(data.snapshot());
        }
        if (snapshots.isEmpty()) {
            return;
        }
        try {
            database.execute("shutdown save", s -> s.savePlayers(snapshots)).get(timeoutSeconds, TimeUnit.SECONDS);
            logger.info("[storage] Saved " + snapshots.size() + " player(s).");
        } catch (Exception ex) {
            logger.error("[storage] Failed to save player data during shutdown: " + ex.getMessage());
        }
    }

    /**
     * Evicts cached entries of players who are no longer online (e.g. a login that was denied by
     * another plugin after LifeCore loaded the data).
     */
    public void evictStale(long graceMillis) {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, PlayerData> entry : cache.entrySet()) {
            UUID uuid = entry.getKey();
            Long loaded = loadedAt.get(uuid);
            if (loaded != null && now - loaded > graceMillis && Bukkit.getPlayer(uuid) == null) {
                PlayerData data = entry.getValue();
                if (data.isDirty()) {
                    save(data);
                }
                cache.remove(uuid, data);
                loadedAt.remove(uuid);
                logger.debug("player", "Evicted stale cache entry", "uuid", uuid);
            }
        }
    }

    // ------------------------------------------------------------------ offline access

    /** Loads data for display purposes. Returns the cached instance for online players. */
    public CompletableFuture<Optional<PlayerData>> loadOffline(UUID uuid) {
        PlayerData cached = cache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(Optional.of(cached));
        }
        return database.submit("load offline " + uuid, s -> s.loadPlayer(uuid))
                .thenApply(snapshot -> snapshot.map(s -> {
                    PlayerData raced = cache.get(uuid);
                    return raced != null ? raced : PlayerData.fromSnapshot(s);
                }));
    }

    /**
     * Resolves a command argument (online name, known offline name or UUID) into a target.
     */
    public CompletableFuture<Optional<Target>> resolve(String input) {
        if (input == null || input.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        if (input.length() == 36) {
            try {
                UUID uuid = UUID.fromString(input);
                PlayerData cached = cache.get(uuid);
                if (cached != null) {
                    return CompletableFuture.completedFuture(Optional.of(new Target(uuid, cached.getName())));
                }
                return database.submit("resolve " + uuid, s -> s.loadPlayer(uuid))
                        .thenApply(s -> s.map(snapshot -> new Target(uuid, snapshot.name())));
            } catch (IllegalArgumentException ignored) {
                // not a UUID, treat as name
            }
        }
        if (!isValidName(input)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            return CompletableFuture.completedFuture(Optional.of(new Target(online.getUniqueId(), online.getName())));
        }
        for (PlayerData data : cache.values()) {
            if (data.getName().equalsIgnoreCase(input)) {
                return CompletableFuture.completedFuture(Optional.of(new Target(data.getUniqueId(), data.getName())));
            }
        }
        return database.submit("resolve " + input, s -> s.loadPlayerByName(input))
                .thenApply(s -> s.map(snapshot -> new Target(snapshot.uuid(), snapshot.name())));
    }

    /**
     * Applies an edit to a player's data, whether online or offline, on a server tick thread.
     * <p>
     * Online (cached) players are edited in place. Offline players are loaded, edited and saved;
     * while that happens a login for the same player waits (see {@link #loadForLogin}).
     *
     * @return the editor's result, or empty if the player has no data
     */
    public <T> CompletableFuture<Optional<T>> edit(UUID uuid, Function<PlayerData, T> editor) {
        CompletableFuture<Optional<T>> result = new CompletableFuture<>();
        List<CompletableFuture<?>> previousHolder = new ArrayList<>(1);
        pendingEdits.compute(uuid, (key, existing) -> {
            if (existing != null) {
                previousHolder.add(existing);
            }
            return result;
        });
        CompletableFuture<Void> start = previousHolder.isEmpty() ? CompletableFuture.completedFuture(null)
                : previousHolder.get(0).handle((a, b) -> null);
        start.thenRun(() -> {
            PlayerData cached = cache.get(uuid);
            if (cached != null) {
                scheduler.runGlobal(() -> applyEdit(cached, editor, result, false));
                return;
            }
            database.submit("load for edit " + uuid, s -> s.loadPlayer(uuid)).whenComplete((snapshot, error) -> {
                if (error != null) {
                    result.completeExceptionally(error);
                    return;
                }
                scheduler.runGlobal(() -> {
                    PlayerData raced = cache.get(uuid);
                    if (raced != null) {
                        applyEdit(raced, editor, result, false);
                    } else if (snapshot.isPresent()) {
                        applyEdit(PlayerData.fromSnapshot(snapshot.get()), editor, result, true);
                    } else {
                        result.complete(Optional.empty());
                    }
                });
            });
        });
        result.whenComplete((r, e) -> pendingEdits.remove(uuid, result));
        return result;
    }

    private <T> void applyEdit(PlayerData data, Function<PlayerData, T> editor, CompletableFuture<Optional<T>> result,
                               @SuppressWarnings("unused") boolean offline) {
        try {
            T value = editor.apply(data);
            if (data.isDirty()) {
                // Queued before the result completes, so a subsequent login load observes this save.
                save(data);
            }
            result.complete(Optional.ofNullable(value));
        } catch (Throwable throwable) {
            logger.error("[player] Edit failed for " + data.getName(), throwable);
            result.completeExceptionally(throwable);
        }
    }

    public CompletableFuture<List<String>> findNames(String prefix, int limit) {
        return database.submit("find names", s -> s.findNames(prefix, limit));
    }

    public Database database() {
        return database;
    }
}
