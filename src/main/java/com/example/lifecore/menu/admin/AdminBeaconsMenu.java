package com.example.lifecore.menu.admin;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.item.beacon.BeaconTier;
import com.example.lifecore.manager.beacon.ActiveBeacon;
import com.example.lifecore.menu.MenuItemSpec;
import com.example.lifecore.menu.MenuLayout;
import com.example.lifecore.menu.PaginatedMenu;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Lists active revive beacons. Left-click: teleport. Right-click: cancel with refund.
 * Shift + right-click: cancel without refund.
 */
public final class AdminBeaconsMenu extends PaginatedMenu<ActiveBeacon> {

    private final List<ActiveBeacon> beacons;

    public AdminBeaconsMenu(LifeCorePlugin plugin, Player viewer, MenuLayout layout, int page) {
        super(plugin, viewer, layout, page);
        this.beacons = new ArrayList<>(plugin.beacons().snapshot());
    }

    @Override
    protected List<ActiveBeacon> entries() {
        return beacons;
    }

    @Override
    protected Set<String> specialItems() {
        return Set.of("back");
    }

    @Override
    protected void build() {
        super.build();
        placeStatic("back", placeholders(), null, click -> plugin.menus().openAdmin(viewer, 0));
    }

    @Override
    protected void placeEntry(int slot, ActiveBeacon beacon) {
        MenuItemSpec template = layout.entry();
        Optional<BeaconTier> tier = plugin.items().beacon(beacon.tierId());
        if (template == null || tier.isEmpty()) {
            return;
        }
        Placeholders ph = plugin.beacons().placeholders(beacon, tier.get());
        set(slot, plugin.menus().items().build(template, viewer, ph, null), click -> handle(beacon, click));
    }

    private void handle(ActiveBeacon beacon, ClickType click) {
        plugin.sounds().play(viewer, "menu-click");
        if (click.isRightClick()) {
            boolean refund = !click.isShiftClick();
            if (plugin.beacons().cancel(beacon, refund, "beacon.cancelled-by-admin")) {
                plugin.messages().send(viewer, "admin.beacon-cancelled", Placeholders.of("target", beacon.targetName()));
                plugin.log().info("admin", "Beacon cancelled", "admin", viewer.getName(), "beacon", beacon.id(), "refund", refund);
            }
            beacons.remove(beacon);
            refresh();
            return;
        }
        Location location = beacon.location();
        if (location == null) {
            plugin.messages().send(viewer, "admin.beacon-world-missing");
            return;
        }
        viewer.closeInventory();
        plugin.scheduler().teleport(viewer, location.clone().add(0.5, 1.0, 0.5));
    }
}
