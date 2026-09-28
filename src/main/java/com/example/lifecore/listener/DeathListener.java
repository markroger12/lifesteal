package com.example.lifecore.listener;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.model.PlayerData;
import com.example.lifecore.util.Guard;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Death processing and post-respawn state restoration.
 */
public final class DeathListener implements Listener {

    private final LifeCorePlugin plugin;

    public DeathListener(LifeCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Guard.run(plugin.log(), "death of " + event.getEntity().getName(), () -> plugin.lifesteal().handleDeath(event));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        plugin.antiExploit().resetDeathGuard(player.getUniqueId());
        plugin.scheduler().runAtEntityLater(player, () -> Guard.run(plugin.log(), "respawn of " + player.getName(), () -> {
            if (!player.isOnline()) {
                return;
            }
            PlayerData data = plugin.players().get(player);
            if (data == null) {
                return;
            }
            plugin.hearts().applyAttribute(player);
            if (data.isEliminated()) {
                plugin.elimination().enforce(player, false);
            } else {
                player.setHealth(plugin.hearts().maxHealth(player));
            }
        }), 1L);
    }
}
