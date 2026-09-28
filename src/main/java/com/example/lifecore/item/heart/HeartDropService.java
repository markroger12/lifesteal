package com.example.lifecore.item.heart;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.configuration.settings.LifeCoreSettings;
import com.example.lifecore.configuration.settings.WorldRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Random heart item drops on player kills (config.yml heart-drop and per-item drop sections).
 */
public final class HeartDropService {

    private final LifeCorePlugin plugin;

    public HeartDropService(LifeCorePlugin plugin) {
        this.plugin = plugin;
    }

    /** Called on the victim's thread during death processing. */
    public void handleKill(Player killer, Player victim, WorldRules rules, boolean legitKill, Location location) {
        if (!rules.lifestealActive() || !rules.heartDrops()) {
            return;
        }
        LifeCoreSettings.HeartDrop global = plugin.settings().heartDrop;
        String world = location.getWorld() == null ? "" : location.getWorld().getName().toLowerCase(Locale.ROOT);
        if (global.enabled() && (legitKill || !global.requireLegitKill())
                && (global.permission().isEmpty() || killer.hasPermission(global.permission()))
                && (global.worlds().isEmpty() || global.worlds().contains(world))
                && roll(global.chance())) {
            Optional<HeartItemDefinition> definition = plugin.items().heart(global.item());
            if (definition.isPresent()) {
                give(killer, location, plugin.items().createHeartItem(definition.get(), global.amount()), global.behavior());
            } else {
                plugin.log().warn("[heart-drop] Unknown heart item '" + global.item() + "' in config.yml heart-drop.item");
            }
        }
        if (!legitKill) {
            return;
        }
        for (HeartItemDefinition definition : plugin.items().hearts()) {
            HeartItemDefinition.DropSpec drop = definition.drop();
            if (drop.enabled() && roll(drop.chance())) {
                give(killer, location, plugin.items().createHeartItem(definition, drop.amount()), drop.behavior());
            }
        }
    }

    private void give(Player killer, Location location, ItemStack item, LifeCoreSettings.DropBehavior behavior) {
        if (behavior == LifeCoreSettings.DropBehavior.INVENTORY && killer.isOnline()) {
            plugin.scheduler().runAtEntity(killer, () -> plugin.itemUse().giveOrDrop(killer, item));
            plugin.messages().send(killer, "heart-drop.received");
            return;
        }
        World world = location.getWorld();
        if (world != null) {
            world.dropItemNaturally(location, item);
            plugin.messages().send(killer, "heart-drop.dropped");
        }
    }

    private static boolean roll(double chancePercent) {
        if (chancePercent <= 0) {
            return false;
        }
        return chancePercent >= 100 || ThreadLocalRandom.current().nextDouble(100.0) < chancePercent;
    }
}
