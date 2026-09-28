package com.example.lifecore.menu.player;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.api.ReviveResult;
import com.example.lifecore.api.ReviveSource;
import com.example.lifecore.manager.revive.ReviveManager;
import com.example.lifecore.menu.MenuItemSpec;
import com.example.lifecore.menu.MenuLayout;
import com.example.lifecore.menu.PaginatedMenu;
import com.example.lifecore.model.EliminatedPlayer;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Lists eliminated players and lets the viewer revive them (paid for players, free for admins).
 */
public final class ReviveMenu extends PaginatedMenu<EliminatedPlayer> {

    private final List<EliminatedPlayer> eliminated;

    public ReviveMenu(LifeCorePlugin plugin, Player viewer, MenuLayout layout, List<EliminatedPlayer> eliminated, int page) {
        super(plugin, viewer, layout, page);
        this.eliminated = eliminated;
    }

    @Override
    protected List<EliminatedPlayer> entries() {
        return eliminated;
    }

    @Override
    protected Placeholders placeholders() {
        return super.placeholders().addAll(plugin.revive().costPlaceholders(viewer)).add("count", eliminated.size());
    }

    @Override
    protected void placeEntry(int slot, EliminatedPlayer entry) {
        MenuItemSpec template = layout.entry();
        if (template == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Placeholders ph = placeholders()
                .add("target", entry.name())
                .add("eliminated_ago", plugin.messages().duration(now - entry.eliminatedAt()))
                .add("cause", plugin.lifesteal().causeName(entry.cause()))
                .add("ban_time", entry.banExpiresAt() == -1 ? plugin.messages().timeUnits().permanent()
                        : entry.banExpiresAt() > now ? plugin.messages().duration(entry.banExpiresAt() - now)
                        : plugin.messages().raw("general.none"));
        set(slot, plugin.menus().items().build(template, viewer, ph, Bukkit.getOfflinePlayer(entry.uuid())), click -> revive(entry, ph));
    }

    private void revive(EliminatedPlayer entry, Placeholders ph) {
        plugin.sounds().play(viewer, "menu-click");
        if (viewer.hasPermission("lifecore.admin.revive")) {
            viewer.closeInventory();
            Placeholders result = ph.copy().add("player", entry.name());
            plugin.admin().revive(entry.uuid(), viewer, -1).thenAccept(outcome -> plugin.scheduler().runAtEntity(viewer,
                    () -> plugin.messages().send(viewer, ReviveManager.resultKey(outcome), result)));
            return;
        }
        if (!viewer.hasPermission("lifecore.revive")) {
            plugin.messages().send(viewer, "errors.no-permission");
            return;
        }
        String problem = plugin.revive().checkPlayerReviveCost(viewer);
        if (problem != null) {
            viewer.closeInventory();
            plugin.messages().send(viewer, problem, ph);
            plugin.sounds().play(viewer, "error");
            return;
        }
        Runnable action = () -> plugin.revive().playerRevive(viewer, entry.uuid(), entry.name(), ReviveSource.MENU)
                .thenAccept(result -> {
                    if (result == ReviveResult.SUCCESS) {
                        plugin.log().debug("menus", "Menu revive", "viewer", viewer.getName(), "target", entry.name());
                    }
                });
        if (plugin.settings().revive.playerRevive().confirm()) {
            plugin.menus().openConfirm(viewer, ph.copy().add("action", ph.apply(plugin.messages().raw("menus.confirm-revive"))), action);
        } else {
            viewer.closeInventory();
            action.run();
        }
    }
}
