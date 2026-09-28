package com.example.lifecore.manager.revive;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.api.HeartChangeReason;
import com.example.lifecore.api.ReviveResult;
import com.example.lifecore.api.ReviveSource;
import com.example.lifecore.configuration.settings.LifeCoreSettings;
import com.example.lifecore.event.PlayerReviveEvent;
import com.example.lifecore.manager.heart.HeartManager;
import com.example.lifecore.manager.heart.HeartMath;
import com.example.lifecore.model.PlayerData;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Revives eliminated players - online or offline - from any source.
 */
public final class ReviveManager {

    private final LifeCorePlugin plugin;
    private final Set<UUID> inProgress = ConcurrentHashMap.newKeySet();
    private volatile List<String> eliminatedNames = List.of();

    public ReviveManager(LifeCorePlugin plugin) {
        this.plugin = plugin;
    }

    private LifeCoreSettings.Revive config() {
        return plugin.settings().revive;
    }

    /**
     * Revives a player by UUID.
     *
     * @param hearts hearts to restore, or a value <= 0 to use revive.hearts
     */
    public CompletableFuture<ReviveResult> revive(UUID target, ReviveSource source, @Nullable UUID reviverId, String reviverName, double hearts) {
        if (!inProgress.add(target)) {
            return CompletableFuture.completedFuture(ReviveResult.ALREADY_IN_PROGRESS);
        }
        return plugin.players().edit(target, data -> reviveNow(data, source, reviverId, reviverName, hearts))
                .handle((result, error) -> {
                    inProgress.remove(target);
                    if (error != null) {
                        plugin.log().warn("[revive] Failed to revive " + target + ": " + error.getMessage());
                        return ReviveResult.ERROR;
                    }
                    return result.orElse(ReviveResult.NOT_FOUND);
                });
    }

    /**
     * Revives loaded data. Must run on a server tick thread.
     */
    public ReviveResult reviveNow(PlayerData data, ReviveSource source, @Nullable UUID reviverId, String reviverName, double hearts) {
        if (!data.isEliminated()) {
            return ReviveResult.NOT_ELIMINATED;
        }
        double restored = hearts > 0 ? hearts : config().hearts();
        LifeCoreSettings.Hearts heartSettings = plugin.settings().hearts;
        restored = HeartMath.clamp(HeartMath.round(restored, heartSettings.halfHearts()), heartSettings.minimum(), heartSettings.hardLimit());
        PlayerReviveEvent event = new PlayerReviveEvent(data.getUniqueId(), data.getName(), source, reviverId, reviverName, restored);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return ReviveResult.CANCELLED;
        }
        Player online = Bukkit.getPlayer(data.getUniqueId());
        boolean onlineAndLoaded = online != null && plugin.players().get(online) == data;
        double finalHearts = HeartMath.clamp(HeartMath.round(event.getHearts(), heartSettings.halfHearts()),
                heartSettings.minimum(), heartSettings.hardLimit());
        if (!data.revive(finalHearts, System.currentTimeMillis(), reviverName, onlineAndLoaded)) {
            return ReviveResult.NOT_ELIMINATED;
        }
        plugin.players().save(data);
        plugin.log().info("revive", "Player revived", "player", data.getName(), "source", source, "by", reviverName,
                "hearts", finalHearts);
        Placeholders ph = new Placeholders()
                .add("player", data.getName())
                .add("reviver", reviverName)
                .add("hearts", plugin.messages().hearts(finalHearts))
                .add("source", plugin.messages().raw("revive.sources." + source.name().toLowerCase(java.util.Locale.ROOT)));
        if (config().broadcast()) {
            plugin.messages().broadcast("revive.broadcast", ph);
            plugin.sounds().playAll("revived-broadcast");
        }
        if (onlineAndLoaded) {
            plugin.scheduler().runAtEntity(online, () -> applyRevivedState(online, data));
        }
        if (source != ReviveSource.BEACON) {
            plugin.beacons().onTargetRevived(data.getUniqueId());
        }
        return ReviveResult.SUCCESS;
    }

    /**
     * Restores an online player after revival (or on join after an offline revival).
     * Must run on the player's owning thread.
     */
    public void applyRevivedState(Player player, PlayerData data) {
        data.clearPendingRevive();
        if (player.isDead()) {
            player.spigot().respawn();
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(config().gamemode());
        }
        plugin.hearts().applyAttribute(player);
        if (config().heal()) {
            player.setHealth(plugin.hearts().maxHealth(player));
            player.setFoodLevel(20);
            player.setSaturation(5f);
            player.setFireTicks(0);
        }
        Location destination = destination(player);
        Runnable effects = () -> {
            Placeholders ph = Placeholders.of("hearts", plugin.messages().hearts(data.getHearts()), "reviver",
                    data.getRevivedBy(), "player", player.getName());
            plugin.messages().send(player, "revive.revived", ph);
            plugin.sounds().play(player, "revived");
            plugin.particles().play("revival", player.getLocation());
        };
        if (destination != null) {
            plugin.scheduler().teleport(player, destination).whenComplete((ok, error) ->
                    plugin.scheduler().runAtEntity(player, effects));
        } else {
            effects.run();
        }
    }

    private @Nullable Location destination(Player player) {
        return switch (config().teleport()) {
            case NONE -> null;
            case BED -> {
                Location bed = player.getRespawnLocation();
                yield bed != null ? bed : worldSpawn();
            }
            case WORLD_SPAWN -> worldSpawn();
        };
    }

    private @Nullable Location worldSpawn() {
        String name = config().spawnWorld();
        World world = name == null || name.isBlank() ? null : Bukkit.getWorld(name);
        if (world == null) {
            List<World> worlds = Bukkit.getWorlds();
            world = worlds.isEmpty() ? null : worlds.get(0);
        }
        return world == null ? null : world.getSpawnLocation().add(0.5, 0, 0.5);
    }

    // ------------------------------------------------------------------ player paid revives

    /** Checks whether a player can pay for a revive right now. Returns null if allowed, else a message key. */
    public @Nullable String checkPlayerReviveCost(Player reviver) {
        LifeCoreSettings.PlayerRevive pr = config().playerRevive();
        if (!pr.enabled()) {
            return "revive.player-disabled";
        }
        PlayerData data = plugin.players().get(reviver);
        if (data == null) {
            return "errors.data-not-loaded";
        }
        if (plugin.combat().isBlocked(reviver, LifeCoreSettings.CombatAction.REVIVE)) {
            return "errors.in-combat";
        }
        if (plugin.cooldowns().remaining("revive", reviver.getUniqueId()) > 0) {
            return "revive.cooldown";
        }
        if (pr.costHearts() > 0 && data.getHearts() - pr.costHearts() < pr.minimumRemaining() - 1e-9) {
            return "revive.not-enough-hearts";
        }
        if (pr.moneyCost() > 0 && !plugin.hooks().hasMoney(reviver, pr.moneyCost())) {
            return plugin.hooks().economyAvailable() ? "revive.not-enough-money" : "revive.economy-missing";
        }
        return null;
    }

    public Placeholders costPlaceholders(Player reviver) {
        LifeCoreSettings.PlayerRevive pr = config().playerRevive();
        return new Placeholders()
                .add("cost", plugin.messages().hearts(pr.costHearts()))
                .add("money", plugin.hooks().formatMoney(pr.moneyCost()))
                .add("minimum", plugin.messages().hearts(pr.minimumRemaining()))
                .add("cooldown", plugin.messages().duration(plugin.cooldowns().remaining("revive", reviver.getUniqueId())));
    }

    /**
     * A player revives another player, paying the configured cost after a successful revive.
     */
    public CompletableFuture<ReviveResult> playerRevive(Player reviver, UUID target, String targetName, ReviveSource source) {
        String problem = checkPlayerReviveCost(reviver);
        if (problem != null) {
            plugin.messages().send(reviver, problem, costPlaceholders(reviver).add("target", targetName));
            plugin.sounds().play(reviver, "error");
            return CompletableFuture.completedFuture(problem.equals("revive.cooldown") ? ReviveResult.ON_COOLDOWN : ReviveResult.INSUFFICIENT_HEARTS);
        }
        if (target.equals(reviver.getUniqueId())) {
            plugin.messages().send(reviver, "revive.self");
            return CompletableFuture.completedFuture(ReviveResult.CANCELLED);
        }
        LifeCoreSettings.PlayerRevive pr = config().playerRevive();
        // Reserve the cooldown immediately so double clicks / spam cannot start two revives.
        plugin.cooldowns().set("revive", reviver.getUniqueId(), pr.cooldownMillis());
        return revive(target, source, reviver.getUniqueId(), reviver.getName(), -1).thenApply(result -> {
            plugin.scheduler().runAtEntity(reviver, () -> finishPlayerRevive(reviver, targetName, result));
            return result;
        });
    }

    private void finishPlayerRevive(Player reviver, String targetName, ReviveResult result) {
        LifeCoreSettings.PlayerRevive pr = config().playerRevive();
        Placeholders ph = costPlaceholders(reviver).add("target", targetName);
        if (!result.isSuccess()) {
            plugin.cooldowns().clear("revive", reviver.getUniqueId());
            plugin.messages().send(reviver, resultKey(result), ph);
            plugin.sounds().play(reviver, "error");
            return;
        }
        PlayerData data = plugin.players().get(reviver);
        if (data != null) {
            if (pr.costHearts() > 0) {
                plugin.hearts().remove(data, reviver, pr.costHearts(), HeartChangeReason.REVIVE_COST, HeartManager.ChangeOptions.standard());
            }
            data.addRevivePerformed();
            plugin.players().save(data);
        }
        if (pr.moneyCost() > 0) {
            plugin.hooks().withdrawMoney(reviver, pr.moneyCost());
        }
        plugin.messages().send(reviver, "revive.player-success", ph);
        plugin.sounds().play(reviver, "success");
    }

    public static String resultKey(ReviveResult result) {
        return switch (result) {
            case SUCCESS -> "revive.success";
            case NOT_FOUND -> "errors.player-not-found";
            case NOT_ELIMINATED -> "revive.not-eliminated";
            case CANCELLED -> "revive.cancelled";
            case INSUFFICIENT_HEARTS -> "revive.not-enough-hearts";
            case INSUFFICIENT_FUNDS -> "revive.not-enough-money";
            case ON_COOLDOWN -> "revive.cooldown";
            case DISABLED -> "revive.player-disabled";
            case ALREADY_IN_PROGRESS -> "revive.in-progress";
            case ERROR -> "errors.internal";
        };
    }

    /** Called on join: finishes offline revivals and bans that expired while offline. */
    public void handleJoin(Player player, PlayerData data, boolean reviveExpiredBan) {
        if (reviveExpiredBan && data.isEliminated()) {
            ReviveResult result = reviveNow(data, ReviveSource.BAN_EXPIRED, null, plugin.messages().raw("revive.sources.ban_expired"), -1);
            if (result.isSuccess()) {
                applyRevivedState(player, data);
                return;
            }
        }
        if (data.isPendingRevive() && !data.isEliminated()) {
            applyRevivedState(player, data);
        }
    }

    /** Names of recently eliminated players (refreshed with the leaderboards) for tab completion. */
    public List<String> eliminatedNames() {
        return eliminatedNames;
    }

    public void setEliminatedNames(List<String> names) {
        this.eliminatedNames = List.copyOf(names);
    }

    public Optional<Boolean> isReviving(UUID uuid) {
        return Optional.of(inProgress.contains(uuid));
    }
}
