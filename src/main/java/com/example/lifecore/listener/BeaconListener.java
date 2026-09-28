package com.example.lifecore.listener;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.item.beacon.BeaconTier;
import com.example.lifecore.manager.beacon.ActiveBeacon;
import com.example.lifecore.util.Guard;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Protects active revive beacons: enemy break attempts and explosions reduce durability,
 * the owner may cancel, and pistons / mobs can never move or remove the block.
 */
public final class BeaconListener implements Listener {

    private final LifeCorePlugin plugin;

    public BeaconListener(LifeCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        Optional<ActiveBeacon> beacon = plugin.beacons().at(event.getBlock());
        if (beacon.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        ActiveBeacon active = beacon.get();
        Guard.run(plugin.log(), "beacon break", () -> {
            if (player.getUniqueId().equals(active.owner())) {
                if (!plugin.beacons().handleOwnerBreak(active)) {
                    plugin.messages().send(player, "beacon.owner-cannot-cancel");
                }
                return;
            }
            Optional<BeaconTier> tier = plugin.items().beacon(active.tierId());
            if (tier.isPresent()) {
                plugin.beacons().damage(active, tier.get().damagePerHit(), player);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    private void handleExplosion(List<Block> blocks) {
        Iterator<Block> iterator = blocks.iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            Optional<ActiveBeacon> beacon = plugin.beacons().at(block);
            if (beacon.isPresent()) {
                iterator.remove();
                plugin.items().beacon(beacon.get().tierId()).ifPresent(tier ->
                        plugin.beacons().damage(beacon.get(), tier.explosionDamage(), null));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (plugin.beacons().at(block).isPresent()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (plugin.beacons().at(block).isPresent()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (plugin.beacons().at(event.getBlock()).isPresent()) {
            event.setCancelled(true);
        }
    }
}
