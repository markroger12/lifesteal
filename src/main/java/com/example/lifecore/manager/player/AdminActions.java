package com.example.lifecore.manager.player;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.api.HeartChangeReason;
import com.example.lifecore.api.HeartChangeResult;
import com.example.lifecore.api.ReviveResult;
import com.example.lifecore.api.ReviveSource;
import com.example.lifecore.manager.heart.HeartManager;
import com.example.lifecore.model.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Administrative operations shared by commands, the admin GUI and the API.
 * All operations work for online and offline players.
 */
public final class AdminActions {

    public enum Operation {SET, ADD, REMOVE}

    private final LifeCorePlugin plugin;

    public AdminActions(LifeCorePlugin plugin) {
        this.plugin = plugin;
    }

    private static String actorName(CommandSender actor) {
        return actor == null ? "API" : actor.getName();
    }

    public CompletableFuture<Optional<HeartChangeResult>> modifyHearts(UUID target, Operation operation, double amount,
                                                                       CommandSender actor, HeartChangeReason reason) {
        String by = actorName(actor);
        return plugin.players().edit(target, data -> {
            Player online = Bukkit.getPlayer(target);
            HeartManager.ChangeOptions options = HeartManager.ChangeOptions.eliminating("ADMIN", by).withIgnoreCap(false);
            HeartChangeResult result = switch (operation) {
                case SET -> plugin.hearts().set(data, online, amount, reason, options);
                case ADD -> plugin.hearts().add(data, online, amount, reason, options);
                case REMOVE -> plugin.hearts().remove(data, online, amount, reason, options);
            };
            plugin.log().info("admin", "Hearts modified", "actor", by, "target", data.getName(), "operation", operation,
                    "amount", amount, "from", result.previous(), "to", result.current());
            return result;
        });
    }

    /** Sets a per-player maximum (value <= 0 restores the configured/permission maximum). */
    public CompletableFuture<Optional<Double>> setMaxHearts(UUID target, double max, CommandSender actor) {
        return plugin.players().edit(target, data -> {
            data.setMaxHeartsOverride(max);
            Player online = Bukkit.getPlayer(target);
            if (online != null) {
                plugin.caps().invalidate(target);
                plugin.scheduler().runAtEntity(online, () -> plugin.hearts().enforceCap(online));
            }
            plugin.log().info("admin", "Max hearts changed", "actor", actorName(actor), "target", data.getName(), "max", max);
            return plugin.hearts().getMaxHearts(data, online);
        });
    }

    public CompletableFuture<Optional<Boolean>> eliminate(UUID target, CommandSender actor) {
        String by = actorName(actor);
        return plugin.players().edit(target, data -> {
            boolean eliminated = plugin.elimination().eliminate(data, Bukkit.getPlayer(target), "ADMIN", by);
            if (eliminated) {
                plugin.log().info("admin", "Player eliminated by admin", "actor", by, "target", data.getName());
            }
            return eliminated;
        });
    }

    public CompletableFuture<ReviveResult> revive(UUID target, CommandSender actor, double hearts) {
        UUID actorId = actor instanceof Player p ? p.getUniqueId() : null;
        return plugin.revive().revive(target, ReviveSource.ADMIN, actorId, actorName(actor), hearts);
    }

    public CompletableFuture<Optional<PlayerData>> reset(UUID target, CommandSender actor) {
        boolean stats = plugin.settings().resetStatistics;
        double starting = plugin.settings().hearts.starting();
        return plugin.players().edit(target, data -> {
            boolean wasEliminated = data.isEliminated();
            data.reset(starting, stats);
            Player online = Bukkit.getPlayer(target);
            if (online != null) {
                plugin.caps().invalidate(target);
                plugin.scheduler().runAtEntity(online, () -> {
                    if (wasEliminated) {
                        plugin.revive().applyRevivedState(online, data);
                    } else {
                        plugin.hearts().applyAttribute(online);
                    }
                });
            }
            plugin.log().info("admin", "Player reset", "actor", actorName(actor), "target", data.getName(), "statistics", stats);
            return data;
        });
    }
}
