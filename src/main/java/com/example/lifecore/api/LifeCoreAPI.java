package com.example.lifecore.api;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public developer API.
 * <p>
 * Synchronous getters only see players whose data is loaded (online players). Use the
 * {@code load...} methods for offline players. Mutating methods work for online and offline players,
 * run on the server thread and complete their futures when the change has been applied.
 * <pre>{@code
 * double hearts = LifeCoreAPI.getHearts(player.getUniqueId());
 * LifeCoreAPI.addHearts(uuid, 2).thenAccept(result -> ...);
 * }</pre>
 */
public final class LifeCoreAPI {

    private static volatile LifeCoreService service;

    private LifeCoreAPI() {
    }

    @ApiStatus.Internal
    public static void register(@Nullable LifeCoreService implementation) {
        service = implementation;
    }

    /** @return true if LifeCore is enabled. */
    public static boolean isAvailable() {
        return service != null;
    }

    private static LifeCoreService service() {
        LifeCoreService current = service;
        if (current == null) {
            throw new IllegalStateException("LifeCore is not enabled");
        }
        return current;
    }

    /** @return data of a loaded (online) player. */
    public static Optional<LifePlayerData> getPlayerData(UUID uuid) {
        return service().getPlayerData(uuid);
    }

    /** Loads data of any player that ever joined. */
    public static CompletableFuture<Optional<LifePlayerData>> loadPlayerData(UUID uuid) {
        return service().loadPlayerData(uuid);
    }

    /** @return hearts of a loaded player, or -1 if the player's data is not loaded. */
    public static double getHearts(UUID uuid) {
        return service().getHearts(uuid);
    }

    /** @return the effective maximum hearts of a loaded player, or -1 if not loaded. */
    public static double getMaxHearts(UUID uuid) {
        return service().getMaxHearts(uuid);
    }

    public static CompletableFuture<Optional<HeartChangeResult>> setHearts(UUID uuid, double hearts) {
        return service().setHearts(uuid, hearts);
    }

    public static CompletableFuture<Optional<HeartChangeResult>> addHearts(UUID uuid, double hearts) {
        return service().addHearts(uuid, hearts);
    }

    public static CompletableFuture<Optional<HeartChangeResult>> removeHearts(UUID uuid, double hearts) {
        return service().removeHearts(uuid, hearts);
    }

    /** @return true once eliminated, false if already eliminated or cancelled. */
    public static CompletableFuture<Boolean> eliminate(UUID uuid) {
        return service().eliminate(uuid);
    }

    public static CompletableFuture<ReviveResult> revive(UUID uuid) {
        return service().revive(uuid, -1);
    }

    public static CompletableFuture<ReviveResult> revive(UUID uuid, double hearts) {
        return service().revive(uuid, hearts);
    }

    /** @return true if a loaded player is eliminated (false if not loaded). */
    public static boolean isEliminated(UUID uuid) {
        return service().isEliminated(uuid);
    }

    public static List<LeaderboardEntry> getLeaderboard(LeaderboardType type) {
        return service().getLeaderboard(type);
    }

    /** Implemented by the plugin. */
    public interface LifeCoreService {
        Optional<LifePlayerData> getPlayerData(UUID uuid);

        CompletableFuture<Optional<LifePlayerData>> loadPlayerData(UUID uuid);

        double getHearts(UUID uuid);

        double getMaxHearts(UUID uuid);

        CompletableFuture<Optional<HeartChangeResult>> setHearts(UUID uuid, double hearts);

        CompletableFuture<Optional<HeartChangeResult>> addHearts(UUID uuid, double hearts);

        CompletableFuture<Optional<HeartChangeResult>> removeHearts(UUID uuid, double hearts);

        CompletableFuture<Boolean> eliminate(UUID uuid);

        CompletableFuture<ReviveResult> revive(UUID uuid, double hearts);

        boolean isEliminated(UUID uuid);

        List<LeaderboardEntry> getLeaderboard(LeaderboardType type);
    }
}
