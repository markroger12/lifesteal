package com.example.lifecore.listener;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.model.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Keeps eliminated players out of normal gameplay (commands, spectator teleports, game mode).
 * Also routes chat input prompts used by the admin menus.
 */
public final class EliminationListener implements Listener {

    private final LifeCorePlugin plugin;

    public EliminationListener(LifeCorePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isEliminated(Player player) {
        PlayerData data = plugin.players().get(player);
        return data != null && data.isEliminated();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!isEliminated(player) || player.hasPermission("lifecore.bypass.elimination")) {
            return;
        }
        if (!plugin.elimination().isAllowedCommand(event.getMessage())) {
            event.setCancelled(true);
            plugin.messages().send(player, "elimination.command-blocked");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE
                && plugin.settings().elimination.preventSpectatorTeleport()
                && isEliminated(event.getPlayer())
                && !event.getPlayer().hasPermission("lifecore.bypass.elimination")) {
            event.setCancelled(true);
            plugin.messages().send(event.getPlayer(), "elimination.teleport-blocked");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        if (!plugin.settings().elimination.lockGamemode() || event.getNewGameMode() == GameMode.SPECTATOR
                || !plugin.settings().elimination.spectator() || player.hasPermission("lifecore.bypass.elimination")) {
            return;
        }
        if (isEliminated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChatInput(AsyncPlayerChatEvent event) {
        if (plugin.chatInput().handle(event.getPlayer(), event.getMessage())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.settings().elimination.canChat() && isEliminated(player) && !player.hasPermission("lifecore.bypass.elimination")) {
            event.setCancelled(true);
            plugin.messages().send(player, "elimination.chat-blocked");
        }
    }
}
