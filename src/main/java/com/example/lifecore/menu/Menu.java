package com.example.lifecore.menu;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for every LifeCore GUI. The menu instance is the inventory holder, which is how
 * clicks are recognised as ours; all clicks inside a LifeCore menu are cancelled.
 */
public abstract class Menu implements InventoryHolder {

    @FunctionalInterface
    public interface ClickHandler {
        void onClick(ClickType click);
    }

    protected final LifeCorePlugin plugin;
    protected final Player viewer;
    protected final MenuLayout layout;
    private final Map<Integer, ClickHandler> handlers = new HashMap<>();
    private Inventory inventory;

    protected Menu(LifeCorePlugin plugin, Player viewer, MenuLayout layout) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.layout = layout;
    }

    /** Placeholders available to every item and the title of this menu. */
    protected Placeholders placeholders() {
        return Placeholders.of("player", viewer.getName());
    }

    /** Populates the inventory. Called on open and refresh. */
    protected abstract void build();

    public void open() {
        String title = plugin.messages().formatItemText(viewer, layout.title(), placeholders());
        if (title.length() > 64) {
            title = title.substring(0, 64);
        }
        inventory = Bukkit.createInventory(this, layout.rows() * 9, title);
        render();
        viewer.openInventory(inventory);
        plugin.sounds().play(viewer, "menu-open");
    }

    public void refresh() {
        if (inventory == null) {
            return;
        }
        render();
    }

    private void render() {
        handlers.clear();
        inventory.clear();
        build();
        if (layout.filler() != null) {
            ItemStack filler = plugin.menus().items().build(layout.filler(), viewer, placeholders(), null);
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                ItemStack current = inventory.getItem(slot);
                if (current == null || current.getType().isAir()) {
                    inventory.setItem(slot, filler);
                }
            }
        }
    }

    protected void set(int slot, ItemStack item, @Nullable ClickHandler handler) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, item);
        if (handler != null) {
            handlers.put(slot, handler);
        } else {
            handlers.remove(slot);
        }
    }

    /**
     * Places a configured static item (all of its slots) with its configured actions plus an optional
     * code-defined handler.
     */
    protected void placeStatic(String key, Placeholders placeholders, @Nullable OfflinePlayer head, @Nullable ClickHandler extra) {
        MenuItemSpec spec = layout.item(key);
        if (spec == null || (!spec.permission().isEmpty() && !viewer.hasPermission(spec.permission()))) {
            return;
        }
        ItemStack item = plugin.menus().items().build(spec, viewer, placeholders, head);
        List<String> actions = spec.actions();
        ClickHandler handler = click -> {
            plugin.sounds().play(viewer, "menu-click");
            if (extra != null) {
                extra.onClick(click);
            }
            if (!actions.isEmpty()) {
                plugin.menus().actions().execute(viewer, actions, placeholders, this);
            }
        };
        for (int slot : spec.slots()) {
            set(slot, item, extra == null && actions.isEmpty() ? null : handler);
        }
    }

    /** Places every configured static item that has no special meaning for the menu. */
    protected void placeAllStatic(Placeholders placeholders, java.util.Set<String> skip) {
        for (String key : layout.items().keySet()) {
            if (!skip.contains(key)) {
                placeStatic(key, placeholders, null, null);
            }
        }
    }

    public void handleClick(int rawSlot, ClickType click) {
        ClickHandler handler = handlers.get(rawSlot);
        if (handler != null) {
            handler.onClick(click);
        }
    }

    /** Called when the inventory closes (any reason). */
    public void handleClose() {
    }

    public Player viewer() {
        return viewer;
    }

    /** @return the LifeCore menu the player currently has open, or null. */
    public static @Nullable Menu openMenu(Player player) {
        org.bukkit.inventory.InventoryView view = player.getOpenInventory();
        if (view == null) {
            return null;
        }
        Inventory top = view.getTopInventory();
        if (top == null) {
            return null;
        }
        return top.getHolder() instanceof Menu menu ? menu : null;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
