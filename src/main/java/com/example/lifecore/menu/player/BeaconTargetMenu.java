package com.example.lifecore.menu.player;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.item.beacon.BeaconTier;
import com.example.lifecore.menu.MenuItemSpec;
import com.example.lifecore.menu.MenuLayout;
import com.example.lifecore.menu.PaginatedMenu;
import com.example.lifecore.model.EliminatedPlayer;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Chooses the player a freshly placed revive beacon will revive.
 */
public final class BeaconTargetMenu extends PaginatedMenu<EliminatedPlayer> {

    private final BeaconTier tier;
    private final List<EliminatedPlayer> candidates;
    private boolean selected;

    public BeaconTargetMenu(LifeCorePlugin plugin, Player viewer, MenuLayout layout, BeaconTier tier, List<EliminatedPlayer> candidates) {
        super(plugin, viewer, layout, 0);
        this.tier = tier;
        this.candidates = candidates;
    }

    @Override
    protected List<EliminatedPlayer> entries() {
        return candidates;
    }

    @Override
    protected Placeholders placeholders() {
        return super.placeholders()
                .add("tier", tier.displayName())
                .add("duration", plugin.messages().duration(tier.durationSeconds() * 1000L));
    }

    @Override
    protected void placeEntry(int slot, EliminatedPlayer entry) {
        MenuItemSpec template = layout.entry();
        if (template == null) {
            return;
        }
        Placeholders ph = placeholders()
                .add("target", entry.name())
                .add("eliminated_ago", plugin.messages().duration(System.currentTimeMillis() - entry.eliminatedAt()))
                .add("cause", plugin.lifesteal().causeName(entry.cause()));
        set(slot, plugin.menus().items().build(template, viewer, ph, Bukkit.getOfflinePlayer(entry.uuid())), click -> {
            if (selected) {
                return;
            }
            selected = true;
            plugin.sounds().play(viewer, "menu-click");
            viewer.closeInventory();
            plugin.beacons().selectTarget(viewer, entry);
        });
    }

    @Override
    public void handleClose() {
        if (!selected) {
            selected = true;
            plugin.beacons().handleSelectionClosed(viewer);
        }
    }
}
