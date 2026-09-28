package com.example.lifecore.api;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.manager.heart.HeartMath;
import com.example.lifecore.manager.player.AdminActions;
import com.example.lifecore.model.PlayerData;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Plugin-side implementation of {@link LifeCoreAPI}. All inputs are validated.
 */
public final class LifeCoreServiceImpl implements LifeCoreAPI.LifeCoreService {

    private final LifeCorePlugin plugin;

    public LifeCoreServiceImpl(LifeCorePlugin plugin) {
        this.plugin = plugin;
    }

    private static double requireAmount(double hearts) {
        if (!Double.isFinite(hearts) || hearts < 0) {
            throw new IllegalArgumentException("Heart amount must be a finite, non-negative number");
        }
        return hearts;
    }

    @Override
    public Optional<LifePlayerData> getPlayerData(UUID uuid) {
        return Optional.ofNullable(plugin.players().get(uuid));
    }

    @Override
    public CompletableFuture<Optional<LifePlayerData>> loadPlayerData(UUID uuid) {
        return plugin.players().loadOffline(uuid).thenApply(data -> data.map(d -> (LifePlayerData) d));
    }

    @Override
    public double getHearts(UUID uuid) {
        return plugin.hearts().getHearts(uuid);
    }

    @Override
    public double getMaxHearts(UUID uuid) {
        PlayerData data = plugin.players().get(uuid);
        return data == null ? -1 : plugin.hearts().getMaxHearts(data, Bukkit.getPlayer(uuid));
    }

    @Override
    public CompletableFuture<Optional<HeartChangeResult>> setHearts(UUID uuid, double hearts) {
        return plugin.admin().modifyHearts(uuid, AdminActions.Operation.SET, Math.min(requireAmount(hearts), HeartMath.MAX_HEALTH_POINTS / 2),
                null, HeartChangeReason.API);
    }

    @Override
    public CompletableFuture<Optional<HeartChangeResult>> addHearts(UUID uuid, double hearts) {
        return plugin.admin().modifyHearts(uuid, AdminActions.Operation.ADD, requireAmount(hearts), null, HeartChangeReason.API);
    }

    @Override
    public CompletableFuture<Optional<HeartChangeResult>> removeHearts(UUID uuid, double hearts) {
        return plugin.admin().modifyHearts(uuid, AdminActions.Operation.REMOVE, requireAmount(hearts), null, HeartChangeReason.API);
    }

    @Override
    public CompletableFuture<Boolean> eliminate(UUID uuid) {
        return plugin.admin().eliminate(uuid, null).thenApply(result -> result.orElse(false));
    }

    @Override
    public CompletableFuture<ReviveResult> revive(UUID uuid, double hearts) {
        return plugin.revive().revive(uuid, ReviveSource.API, null, "API", Double.isFinite(hearts) ? hearts : -1);
    }

    @Override
    public boolean isEliminated(UUID uuid) {
        PlayerData data = plugin.players().get(uuid);
        return data != null && data.isEliminated();
    }

    @Override
    public List<LeaderboardEntry> getLeaderboard(LeaderboardType type) {
        return plugin.leaderboards().get(type);
    }
}
