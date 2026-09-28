package com.example.lifecore.manager.elimination;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.configuration.settings.LifeCoreSettings;
import com.example.lifecore.event.PlayerEliminationEvent;
import com.example.lifecore.model.PlayerData;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * Eliminates players, decides ban durations and enforces the eliminated state.
 */
public final class EliminationManager {

    private final LifeCorePlugin plugin;

    public EliminationManager(LifeCorePlugin plugin) {
        this.plugin = plugin;
    }

    private LifeCoreSettings.Elimination config() {
        return plugin.settings().elimination;
    }

    // ------------------------------------------------------------------ ban durations

    /**
     * Resolves the ban duration using permission tiers. The shortest applicable duration wins.
     *
     * @param hasPermission permission check for the player (online check or LuckPerms lookup)
     * @return milliseconds; 0 = no ban, -1 = permanent
     */
    public static long resolveBanDuration(LifeCoreSettings.Ban ban, Predicate<String> hasPermission) {
        if (!ban.enabled()) {
            return 0;
        }
        long best = ban.defaultDurationMillis();
        for (LifeCoreSettings.PermissionDuration tier : ban.tiers()) {
            if (!hasPermission.test(tier.permission())) {
                continue;
            }
            best = shorter(best, tier.millis());
        }
        if (best == -1 && !ban.allowPermanent()) {
            return 0;
        }
        return best;
    }

    private static long shorter(long a, long b) {
        if (a == -1) {
            return b;
        }
        if (b == -1) {
            return a;
        }
        return Math.min(a, b);
    }

    public long banDurationFor(@Nullable Player online, UUID uuid) {
        LifeCoreSettings.Ban ban = config().ban();
        if (online != null) {
            return resolveBanDuration(ban, online::hasPermission);
        }
        return resolveBanDuration(ban, permission -> plugin.hooks().hasOfflinePermission(uuid, permission));
    }

    // ------------------------------------------------------------------ elimination

    /**
     * Eliminates a player. Must run on a server tick thread.
     *
     * @return true if the player was eliminated (false if already eliminated or the event was cancelled)
     */
    public boolean eliminate(PlayerData data, @Nullable Player online, String cause, @Nullable String killerName) {
        if (data.isEliminated()) {
            return false;
        }
        if (online != null && online.hasPermission("lifecore.bypass.elimination")) {
            plugin.log().debug("elimination", "bypassed by permission", "player", data.getName());
            return false;
        }
        long banMillis = banDurationFor(online, data.getUniqueId());
        PlayerEliminationEvent event = new PlayerEliminationEvent(data.getUniqueId(), data.getName(), cause, killerName, banMillis);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        long now = System.currentTimeMillis();
        long duration = event.getBanDurationMillis();
        if (duration == -1 && !config().ban().allowPermanent()) {
            duration = config().ban().defaultDurationMillis() == -1 ? 0 : config().ban().defaultDurationMillis();
        }
        long banExpiresAt = duration == 0 ? 0 : duration == -1 ? -1 : now + duration;
        if (!data.eliminate(now, cause, killerName == null ? "" : killerName, banExpiresAt)) {
            return false;
        }
        plugin.log().info("elimination", "Player eliminated", "player", data.getName(), "cause", cause,
                "killer", killerName == null || killerName.isEmpty() ? "-" : killerName,
                "ban", duration == 0 ? "none" : plugin.messages().duration(duration));
        plugin.players().save(data);

        Placeholders ph = placeholders(data, cause, killerName, duration);
        if (config().broadcast()) {
            plugin.messages().broadcast("elimination.broadcast", ph);
            plugin.sounds().playAll("eliminated-broadcast");
        }
        if (online != null) {
            Location location = online.getLocation();
            plugin.scheduler().runAtEntity(online, () -> {
                plugin.messages().send(online, "elimination.eliminated", ph);
                plugin.sounds().play(online, "eliminated");
                plugin.particles().play("elimination", location);
                if (config().lightning()) {
                    World world = location.getWorld();
                    if (world != null) {
                        com.example.lifecore.util.Guard.cosmetic(plugin.log(), "lightning", () -> world.strikeLightningEffect(location));
                    }
                }
                plugin.hearts().applyAttribute(online);
            });
            plugin.scheduler().runAtEntityLater(online, () -> enforce(online, true), 2L);
        }
        return true;
    }

    /** Eliminates a player by UUID (online or offline) - used by admin commands and the API. */
    public CompletableFuture<Optional<Boolean>> eliminate(UUID uuid, String cause, String by) {
        return plugin.players().edit(uuid, data -> eliminate(data, Bukkit.getPlayer(uuid), cause, by));
    }

    public Placeholders placeholders(PlayerData data, String cause, @Nullable String killerName, long banMillis) {
        return new Placeholders()
                .add("player", data.getName())
                .add("cause", plugin.lifesteal().causeName(cause))
                .add("killer", killerName == null || killerName.isEmpty() ? plugin.messages().raw("general.none") : killerName)
                .add("ban_time", banMillis == 0 ? plugin.messages().raw("general.none") : plugin.messages().duration(banMillis))
                .add("hearts", plugin.messages().hearts(data.getHeartsBeforeElimination()));
    }

    // ------------------------------------------------------------------ enforcement

    /**
     * Applies the eliminated state to an online player: kick for bans, spectator mode otherwise.
     * Must run on the player's owning thread.
     */
    public void enforce(Player player, boolean justEliminated) {
        PlayerData data = plugin.players().get(player);
        if (data == null || !data.isEliminated() || !player.isOnline()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (data.isBannedAt(now)) {
            player.kickPlayer(banScreen(data, now));
            return;
        }
        if (justEliminated && config().kick()) {
            player.kickPlayer(plugin.messages().text(player, "elimination.kick", placeholders(data,
                    data.getEliminationCause(), data.getEliminatedBy(), 0)));
            return;
        }
        if (config().spectator()) {
            if (player.isDead()) {
                if (config().autoRespawn()) {
                    player.spigot().respawn();
                }
                return;
            }
            if (player.getGameMode() != GameMode.SPECTATOR) {
                player.setGameMode(GameMode.SPECTATOR);
            }
            plugin.messages().send(player, "elimination.spectating", placeholders(data, data.getEliminationCause(),
                    data.getEliminatedBy(), 0));
            return;
        }
        player.kickPlayer(plugin.messages().text(player, "elimination.locked", placeholders(data,
                data.getEliminationCause(), data.getEliminatedBy(), 0)));
    }

    public String banScreen(PlayerData data, long now) {
        long remaining = data.getRemainingBanMillis(now);
        Placeholders ph = placeholders(data, data.getEliminationCause(), data.getEliminatedBy(), remaining)
                .add("time", remaining == -1 ? plugin.messages().timeUnits().permanent() : plugin.messages().duration(remaining));
        return plugin.messages().text("elimination.ban-screen", ph);
    }

    /** Login decision for an eliminated player. */
    public record LoginDecision(boolean allowed, String kickMessage, boolean reviveOnJoin) {
        public static final LoginDecision ALLOW = new LoginDecision(true, "", false);
    }

    /**
     * Decides whether an eliminated player may join. Runs on the async pre-login thread and
     * therefore never fires events or touches the world; revivals for expired bans are applied on join.
     */
    public LoginDecision checkLogin(PlayerData data, boolean bypassBan) {
        if (!data.isEliminated()) {
            return LoginDecision.ALLOW;
        }
        long now = System.currentTimeMillis();
        if (data.isBannedAt(now)) {
            if (bypassBan) {
                return LoginDecision.ALLOW;
            }
            return new LoginDecision(false, banScreen(data, now), false);
        }
        boolean banExpired = data.getBanExpiresAt() > 0 && data.getBanExpiresAt() <= now;
        if (banExpired && config().ban().onExpire() == LifeCoreSettings.OnExpire.REVIVE) {
            return new LoginDecision(true, "", true);
        }
        if (config().spectator() || bypassBan) {
            return LoginDecision.ALLOW;
        }
        return new LoginDecision(false, plugin.messages().text("elimination.locked",
                placeholders(data, data.getEliminationCause(), data.getEliminatedBy(), 0)), false);
    }

    public boolean isAllowedCommand(String commandLine) {
        String label = commandLine.startsWith("/") ? commandLine.substring(1) : commandLine;
        int space = label.indexOf(' ');
        if (space >= 0) {
            label = label.substring(0, space);
        }
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        return config().allowedCommands().contains(label.toLowerCase(java.util.Locale.ROOT));
    }
}
