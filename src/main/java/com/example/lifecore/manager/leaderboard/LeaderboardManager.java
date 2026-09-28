package com.example.lifecore.manager.leaderboard;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.api.LeaderboardEntry;
import com.example.lifecore.api.LeaderboardType;
import com.example.lifecore.configuration.settings.LifeCoreSettings;
import com.example.lifecore.model.PlayerData;
import com.example.lifecore.model.PlayerSnapshot;
import com.example.lifecore.util.scheduler.ScheduledTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cached leaderboards, refreshed asynchronously.
 * <p>
 * A refresh snapshots online players (whose newest values may not be saved yet), queries the
 * database for the top rows and merges both, so leaderboards are accurate without saving first.
 * Readers only ever see immutable lists.
 */
public final class LeaderboardManager {

    private final LifeCorePlugin plugin;
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private volatile Map<LeaderboardType, List<LeaderboardEntry>> boards = new EnumMap<>(LeaderboardType.class);
    private volatile long lastRefresh;
    private ScheduledTask task = ScheduledTask.NOOP;

    public LeaderboardManager(LifeCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task.cancel();
        LifeCoreSettings.Leaderboards config = plugin.settings().leaderboards;
        if (!config.enabled()) {
            return;
        }
        long period = config.refreshSeconds() * 20L;
        task = plugin.scheduler().runGlobalTimer(this::refresh, 40L, period);
    }

    public void stop() {
        task.cancel();
    }

    public List<LeaderboardEntry> get(LeaderboardType type) {
        return boards.getOrDefault(type, List.of());
    }

    public long lastRefresh() {
        return lastRefresh;
    }

    /** Starts an asynchronous refresh (no-op if one is running). Call from a tick thread. */
    public void refresh() {
        if (!refreshing.compareAndSet(false, true)) {
            return;
        }
        LifeCoreSettings.Leaderboards config = plugin.settings().leaderboards;
        List<PlayerSnapshot> online = new ArrayList<>();
        for (PlayerData data : plugin.players().cached()) {
            online.add(data.snapshot());
        }
        int limit = config.size();
        int queryLimit = limit + online.size();
        plugin.database().submit("leaderboards", storage -> {
            Map<LeaderboardType, List<LeaderboardEntry>> fromDb = new EnumMap<>(LeaderboardType.class);
            for (LeaderboardType type : LeaderboardType.values()) {
                fromDb.put(type, storage.top(type, queryLimit, config.excludeEliminated()));
            }
            List<String> eliminated = new ArrayList<>();
            for (var entry : storage.listEliminated(100)) {
                eliminated.add(entry.name());
            }
            plugin.revive().setEliminatedNames(eliminated);
            return fromDb;
        }).whenComplete((fromDb, error) -> {
            try {
                if (error != null) {
                    plugin.log().debug("leaderboard", () -> "refresh failed: " + error.getMessage());
                    return;
                }
                Map<LeaderboardType, List<LeaderboardEntry>> merged = new EnumMap<>(LeaderboardType.class);
                for (LeaderboardType type : LeaderboardType.values()) {
                    merged.put(type, merge(type, fromDb.get(type), online, limit, config.excludeEliminated()));
                }
                boards = merged;
                lastRefresh = System.currentTimeMillis();
            } finally {
                refreshing.set(false);
            }
        });
    }

    /** Merges database rows with fresher online values. Package-private for tests. */
    static List<LeaderboardEntry> merge(LeaderboardType type, List<LeaderboardEntry> database, List<PlayerSnapshot> online,
                                        int limit, boolean excludeEliminated) {
        Map<UUID, LeaderboardEntry> combined = new HashMap<>();
        for (LeaderboardEntry entry : database) {
            combined.put(entry.uuid(), entry);
        }
        for (PlayerSnapshot snapshot : online) {
            if (type == LeaderboardType.HEARTS && excludeEliminated && snapshot.eliminated()) {
                combined.remove(snapshot.uuid());
                continue;
            }
            combined.put(snapshot.uuid(), new LeaderboardEntry(0, snapshot.uuid(), snapshot.name(), value(type, snapshot)));
        }
        List<LeaderboardEntry> sorted = new ArrayList<>(combined.values());
        sorted.sort(Comparator.comparingDouble(LeaderboardEntry::value).reversed()
                .thenComparing(e -> e.name() == null ? "" : e.name().toLowerCase(Locale.ROOT)));
        List<LeaderboardEntry> result = new ArrayList<>(Math.min(limit, sorted.size()));
        for (int i = 0; i < sorted.size() && i < limit; i++) {
            LeaderboardEntry entry = sorted.get(i);
            result.add(new LeaderboardEntry(i + 1, entry.uuid(), entry.name(), entry.value()));
        }
        return List.copyOf(result);
    }

    private static double value(LeaderboardType type, PlayerSnapshot snapshot) {
        return switch (type) {
            case HEARTS -> snapshot.hearts();
            case KILLS -> snapshot.kills();
            case DEATHS -> snapshot.deaths();
            case REVIVES -> snapshot.revives();
        };
    }
}
