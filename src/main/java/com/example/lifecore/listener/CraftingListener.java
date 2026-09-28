package com.example.lifecore.listener;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.item.RecipeManager;
import org.bukkit.Keyed;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.Optional;
import java.util.Set;

/**
 * Validates LifeCore recipes and prevents LifeCore items from being consumed by vanilla
 * mechanics (crafting, anvils, furnaces, looms, trading, ...).
 */
public final class CraftingListener implements Listener {

    /** Inventory types (by name, stable across versions) LifeCore items may never be put into. */
    private static final Set<String> RESTRICTED = Set.of("ANVIL", "GRINDSTONE", "SMITHING", "LOOM", "CARTOGRAPHY",
            "ENCHANTING", "BEACON", "FURNACE", "BLAST_FURNACE", "SMOKER", "BREWING", "STONECUTTER", "MERCHANT", "CRAFTER");

    private final LifeCorePlugin plugin;

    public CraftingListener(LifeCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        ItemStack[] matrix = inventory.getMatrix();
        Recipe recipe = event.getRecipe();
        if (recipe instanceof Keyed keyed && plugin.recipes().isOwn(keyed.getKey())) {
            Optional<RecipeManager.RecipeInfo> info = plugin.recipes().info(keyed.getKey());
            if (info.isEmpty()) {
                return;
            }
            HumanEntity viewer = event.getView().getPlayer();
            if (!info.get().permission().isEmpty() && !viewer.hasPermission(info.get().permission())) {
                inventory.setResult(null);
                return;
            }
            if (!plugin.recipes().validateMatrix(info.get(), matrix)) {
                inventory.setResult(null);
                return;
            }
            if (info.get().uniqueResult()) {
                inventory.setResult(plugin.items().create(info.get().resultReference(), 1).orElse(null));
            }
            return;
        }
        if (plugin.recipes().containsCustomItem(matrix)) {
            inventory.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof Keyed keyed) || !plugin.recipes().isOwn(keyed.getKey())) {
            if (plugin.recipes().containsCustomItem(event.getInventory().getMatrix())) {
                event.setCancelled(true);
            }
            return;
        }
        Optional<RecipeManager.RecipeInfo> info = plugin.recipes().info(keyed.getKey());
        if (info.isEmpty() || !plugin.recipes().validateMatrix(info.get(), event.getInventory().getMatrix())) {
            event.setCancelled(true);
            return;
        }
        if (info.get().uniqueResult() && event.isShiftClick() && plugin.beacons().settings().blockShiftCrafting()) {
            event.setCancelled(true);
            plugin.messages().send(event.getWhoClicked(), "beacon.craft-one-at-a-time");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!RESTRICTED.contains(String.valueOf(top.getType()))) {
            return;
        }
        boolean clickedTop = event.getClickedInventory() == top;
        if (clickedTop && plugin.items().isLifeCoreItem(event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        if (!clickedTop && event.isShiftClick() && plugin.items().isLifeCoreItem(event.getCurrentItem())) {
            event.setCancelled(true);
            return;
        }
        InventoryAction action = event.getAction();
        if (clickedTop && (action == InventoryAction.HOTBAR_SWAP || String.valueOf(action).equals("HOTBAR_MOVE_AND_READD"))) {
            int button = event.getHotbarButton();
            ItemStack hotbar = button >= 0 ? event.getWhoClicked().getInventory().getItem(button)
                    : event.getWhoClicked().getInventory().getItemInOffHand();
            if (plugin.items().isLifeCoreItem(hotbar)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!RESTRICTED.contains(String.valueOf(top.getType())) || !plugin.items().isLifeCoreItem(event.getOldCursor())) {
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (slot < top.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvil(PrepareAnvilEvent event) {
        for (ItemStack item : event.getInventory().getContents()) {
            if (plugin.items().isLifeCoreItem(item)) {
                event.setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent event) {
        if (RESTRICTED.contains(String.valueOf(event.getDestination().getType())) && plugin.items().isLifeCoreItem(event.getItem())) {
            event.setCancelled(true);
        }
    }
}
