package com.example.lifecore.listener;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.menu.Menu;
import com.example.lifecore.util.Guard;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Routes clicks in LifeCore menus and makes every menu read-only.
 */
public final class MenuListener implements Listener {

    private final LifeCorePlugin plugin;

    public MenuListener(LifeCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof Menu menu)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != top || !menu.viewer().equals(event.getWhoClicked())) {
            return;
        }
        Guard.run(plugin.log(), "menu click", () -> menu.handleClick(event.getRawSlot(), event.getClick()));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Menu menu) {
            Guard.run(plugin.log(), "menu close", menu::handleClose);
        }
    }
}
