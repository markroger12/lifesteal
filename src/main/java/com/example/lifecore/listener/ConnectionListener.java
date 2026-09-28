package com.example.lifecore.listener;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.manager.elimination.EliminationManager;
import com.example.lifecore.model.PlayerData;
import com.example.lifecore.util.Guard;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player data lifecycle: load before login, apply on join, persist on quit.
 */
public final class ConnectionListener implements Listener {

    private final LifeCorePlugin plugin;
    private final Set<UUID> reviveOnJoin = ConcurrentHashMap.newKeySet();

    public ConnectionListener(LifeCorePlugin plugin) {
        this.plugin = plugin;
    }

    /** Loads data on the async login thread and denies eliminated/banned players. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID uuid = event.getUniqueId();
        String name = event.getName();
        PlayerData data;
        try {
            data = plugin.players().loadForLogin(uuid, name, plugin.storageSettings().loadTimeoutSeconds());
        } catch (Exception ex) {
            plugin.log().warn("login", "Could not load player data", "player", name, "error", ex.getMessage());
            if (plugin.storageSettings().denyLoginOnLoadFailure()) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, plugin.messages().text("errors.login-load-failed", Placeholders.EMPTY));
            }
            return;
        }
        try {
            boolean bypass = plugin.hooks().hasOfflinePermission(uuid, "lifecore.bypass.ban");
            EliminationManager.LoginDecision decision = plugin.elimination().checkLogin(data, bypass);
            if (!decision.allowed()) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, decision.kickMessage());
                plugin.players().discard(uuid);
                return;
            }
            if (decision.reviveOnJoin()) {
                reviveOnJoin.add(uuid);
            }
            plugin.antiExploit().ips().record(uuid, event.getAddress());
        } catch (RuntimeException ex) {
            plugin.log().error("Login check failed for " + name, ex);
        }
    }

    /** Another plugin denied the login after we loaded the data: drop it again. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLoginMonitor(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            reviveOnJoin.remove(event.getUniqueId());
            plugin.players().discard(event.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Guard.run(plugin.log(), "join of " + player.getName(), () -> {
            PlayerData data = plugin.players().get(player);
            if (data == null) {
                plugin.messages().send(player, "errors.data-not-loaded");
                return;
            }
            data.setName(player.getName());
            data.setLastSeen(System.currentTimeMillis());
            plugin.antiExploit().resetDeathGuard(player.getUniqueId());
            plugin.caps().invalidate(player.getUniqueId());
            if (player.getAddress() != null) {
                plugin.antiExploit().ips().record(player.getUniqueId(), player.getAddress().getAddress());
            }
            boolean reviveExpired = reviveOnJoin.remove(player.getUniqueId());
            if (reviveExpired || data.isPendingRevive()) {
                plugin.revive().handleJoin(player, data, reviveExpired);
            }
            if (data.isEliminated()) {
                plugin.hearts().applyAttribute(player);
                plugin.scheduler().runAtEntityLater(player, () -> plugin.elimination().enforce(player, false), 5L);
            } else {
                if (plugin.settings().hearts.enforceCapOnJoin()) {
                    plugin.hearts().enforceCap(player);
                } else {
                    plugin.hearts().applyAttribute(player);
                }
            }
            if (!plugin.recipes().keys().isEmpty()) {
                player.discoverRecipes(plugin.recipes().keys());
            }
            if (plugin.settings().resourcePack.sendOnJoin()) {
                plugin.scheduler().runAtEntityLater(player, () -> plugin.resourcePack().send(player), 40L);
            }
        });
    }

    /** Combat logging: players who disconnect while tagged are killed (the attacker gets the kill). */
    @EventHandler(priority = EventPriority.LOW)
    public void onQuitCombat(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Guard.run(plugin.log(), "combat-log check of " + player.getName(), () -> {
            boolean kicked = plugin.combat().consumeKicked(player.getUniqueId());
            if (!plugin.settings().combat.punishCombatLogging() || kicked || player.isDead()
                    || !plugin.combat().isTaggedInternally(player.getUniqueId())
                    || player.hasPermission("lifecore.bypass.combatlog")) {
                return;
            }
            PlayerData data = plugin.players().get(player);
            GameMode mode = player.getGameMode();
            if (data == null || data.isEliminated() || mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
                return;
            }
            plugin.log().info("combat", "Combat logger killed", "player", player.getName());
            plugin.messages().broadcast("combat.logged-out", Placeholders.of("player", player.getName()));
            player.setHealth(0.0);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Guard.run(plugin.log(), "quit of " + player.getName(), () -> {
            plugin.beacons().cancelPending(player, true, null);
            plugin.chatInput().clear(uuid);
            plugin.antiExploit().forget(uuid);
            plugin.caps().invalidate(uuid);
            reviveOnJoin.remove(uuid);
            plugin.players().handleQuit(uuid);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) {
        plugin.combat().markKicked(event.getPlayer().getUniqueId());
    }
}
