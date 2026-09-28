package com.example.lifecore.item;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.configuration.settings.LifeCoreSettings;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;
import java.util.Map;

/**
 * Shared checks and inventory helpers for all LifeCore item interactions.
 */
public final class ItemUseSupport {

    private final LifeCorePlugin plugin;

    public ItemUseSupport(LifeCorePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * World, creative-mode and combat checks common to every item use.
     *
     * @return true if the use may continue
     */
    public boolean preChecks(Player player, LifeCoreSettings.CombatAction action) {
        if (!plugin.worlds().get(player.getWorld()).items()) {
            plugin.messages().send(player, "items.disabled-world");
            plugin.sounds().play(player, "error");
            return false;
        }
        if (player.getGameMode() == GameMode.CREATIVE && plugin.settings().antiExploit.blockCreativeItemUse()
                && !player.hasPermission("lifecore.admin")) {
            plugin.messages().send(player, "items.creative-blocked");
            plugin.sounds().play(player, "error");
            return false;
        }
        if (plugin.combat().isBlocked(player, action)) {
            plugin.messages().send(player, "errors.in-combat");
            plugin.sounds().play(player, "error");
            return false;
        }
        return true;
    }

    /**
     * Removes exactly one of the given item from the hand, verifying the hand still holds it.
     *
     * @return false if the item changed in the meantime (nothing removed)
     */
    public boolean consumeOne(Player player, EquipmentSlot hand, ItemStack expected) {
        PlayerInventory inventory = player.getInventory();
        ItemStack current = inventory.getItem(hand);
        if (current == null || current.getType().isAir() || !current.isSimilar(expected)) {
            return false;
        }
        if (current.getAmount() > 1) {
            current.setAmount(current.getAmount() - 1);
            inventory.setItem(hand, current);
        } else {
            inventory.setItem(hand, null);
        }
        return true;
    }

    /** Gives an item, dropping leftovers at the player's feet. Must run on the player's thread. */
    public void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            Location location = player.getLocation();
            World world = location.getWorld();
            if (world != null) {
                for (ItemStack leftover : leftovers.values()) {
                    world.dropItemNaturally(location, leftover);
                }
            }
            plugin.messages().send(player, "items.dropped-full-inventory");
        }
    }

    public boolean hasSpace(Player player, ItemStack item) {
        PlayerInventory inventory = player.getInventory();
        if (inventory.firstEmpty() >= 0) {
            return true;
        }
        for (ItemStack content : inventory.getStorageContents()) {
            if (content != null && content.isSimilar(item) && content.getAmount() + item.getAmount() <= content.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    /** Runs console commands with {player} replaced. */
    public void runCommands(List<String> commands, Player player) {
        runCommands(commands, Placeholders.of("player", player.getName(), "uuid", player.getUniqueId()));
    }

    public void runCommands(List<String> commands, Placeholders placeholders) {
        if (commands == null || commands.isEmpty()) {
            return;
        }
        plugin.scheduler().runGlobal(() -> {
            for (String command : commands) {
                String line = placeholders.apply(command);
                if (line.startsWith("/")) {
                    line = line.substring(1);
                }
                if (!line.isBlank()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
                }
            }
        });
    }

    /**
     * Handles a forged or tampered LifeCore item: logs it, alerts staff and confiscates it if configured.
     */
    public void handleInvalid(Player player, EquipmentSlot hand, ItemStack item, String reason) {
        LifeCoreSettings.Security security = plugin.settings().security;
        if (security.logInvalid()) {
            plugin.log().warn("security", "Rejected invalid LifeCore item", "player", player.getName(), "reason", reason,
                    "material", item.getType());
        }
        Placeholders ph = Placeholders.of("player", player.getName(), "reason", reason);
        plugin.messages().broadcastPermission("security.staff-alert", ph, "lifecore.notify");
        if (security.confiscateInvalid()) {
            ItemStack current = player.getInventory().getItem(hand);
            if (current != null && current.isSimilar(item)) {
                player.getInventory().setItem(hand, null);
            }
            plugin.messages().send(player, "security.item-confiscated");
        } else {
            plugin.messages().send(player, "security.item-invalid");
        }
        plugin.sounds().play(player, "error");
    }
}
