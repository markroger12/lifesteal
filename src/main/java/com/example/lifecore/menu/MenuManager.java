package com.example.lifecore.menu;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.api.LeaderboardType;
import com.example.lifecore.configuration.settings.LifeCoreSettings;
import com.example.lifecore.item.beacon.BeaconTier;
import com.example.lifecore.menu.admin.AdminBeaconsMenu;
import com.example.lifecore.menu.admin.AdminItemsMenu;
import com.example.lifecore.menu.admin.AdminMenu;
import com.example.lifecore.menu.admin.AdminPlayerMenu;
import com.example.lifecore.menu.player.BeaconTargetMenu;
import com.example.lifecore.menu.player.CheckMenu;
import com.example.lifecore.menu.player.LeaderboardMenu;
import com.example.lifecore.menu.player.PlayerMenu;
import com.example.lifecore.menu.player.ReviveMenu;
import com.example.lifecore.model.EliminatedPlayer;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Loads menu layouts from menus.yml and opens menus.
 */
public final class MenuManager {

    public static final List<String> MENU_IDS = List.of("player-menu", "revive-menu", "leaderboard-menu", "check-menu",
            "beacon-target-menu", "confirm-menu", "admin-menu", "admin-player-menu", "admin-items-menu", "admin-beacons-menu");

    private final LifeCorePlugin plugin;
    private final MenuItemBuilder itemBuilder;
    private final MenuActions actions;
    private volatile Map<String, MenuLayout> layouts = Map.of();

    public MenuManager(LifeCorePlugin plugin) {
        this.plugin = plugin;
        this.itemBuilder = new MenuItemBuilder(plugin);
        this.actions = new MenuActions(plugin);
    }

    public List<String> load(YamlConfiguration config, @Nullable YamlConfiguration defaults) {
        List<String> problems = new ArrayList<>();
        Map<String, MenuLayout> parsed = new HashMap<>();
        for (String id : MENU_IDS) {
            ConfigurationSection section = config.isConfigurationSection(id) && !config.getConfigurationSection(id).getKeys(false).isEmpty()
                    ? config.getConfigurationSection(id) : null;
            if (section == null && defaults != null) {
                section = defaults.getConfigurationSection(id);
                problems.add("menus.yml -> " + id + ": missing menu section, the bundled default is used");
            }
            if (section == null) {
                continue;
            }
            parsed.put(id, MenuLayout.parse(id, section, p -> problems.add("menus.yml -> " + p)));
        }
        this.layouts = Collections.unmodifiableMap(parsed);
        return problems;
    }

    public MenuItemBuilder items() {
        return itemBuilder;
    }

    public MenuActions actions() {
        return actions;
    }

    private @Nullable MenuLayout layout(Player viewer, String id) {
        MenuLayout layout = layouts.get(id);
        if (layout == null) {
            plugin.messages().send(viewer, "menus.unavailable", Placeholders.of("menu", id));
        }
        return layout;
    }

    private boolean blockedByCombat(Player viewer) {
        if (plugin.combat().isBlocked(viewer, LifeCoreSettings.CombatAction.MENU)) {
            plugin.messages().send(viewer, "errors.in-combat");
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ openers (call on the viewer's thread)

    public void openPlayerMenu(Player viewer) {
        MenuLayout layout = layout(viewer, "player-menu");
        if (layout != null && !blockedByCombat(viewer)) {
            new PlayerMenu(plugin, viewer, layout).open();
        }
    }

    public void openReviveMenu(Player viewer, int page) {
        MenuLayout layout = layout(viewer, "revive-menu");
        if (layout == null || blockedByCombat(viewer)) {
            return;
        }
        plugin.database().submit("list eliminated", s -> s.listEliminated(270)).whenComplete((list, error) ->
                plugin.scheduler().runAtEntity(viewer, () -> {
                    if (error != null) {
                        plugin.messages().send(viewer, "errors.database-unavailable");
                        return;
                    }
                    new ReviveMenu(plugin, viewer, layout, list, page).open();
                }));
    }

    public void openLeaderboard(Player viewer, LeaderboardType type, int page) {
        MenuLayout layout = layout(viewer, "leaderboard-menu");
        if (layout != null) {
            new LeaderboardMenu(plugin, viewer, layout, type, page).open();
        }
    }

    public void openCheck(Player viewer, UUID target) {
        MenuLayout layout = layout(viewer, "check-menu");
        if (layout == null) {
            return;
        }
        plugin.players().loadOffline(target).whenComplete((data, error) -> plugin.scheduler().runAtEntity(viewer, () -> {
            if (error != null) {
                plugin.messages().send(viewer, "errors.database-unavailable");
            } else if (data.isEmpty()) {
                plugin.messages().send(viewer, "errors.player-not-found", Placeholders.of("player", target));
            } else {
                new CheckMenu(plugin, viewer, layout, data.get()).open();
            }
        }));
    }

    public void openBeaconTargets(Player viewer, BeaconTier tier, List<EliminatedPlayer> candidates) {
        MenuLayout layout = layouts.get("beacon-target-menu");
        if (layout == null) {
            plugin.beacons().handleSelectionClosed(viewer);
            return;
        }
        new BeaconTargetMenu(plugin, viewer, layout, tier, candidates).open();
    }

    public void openConfirm(Player viewer, Placeholders context, Runnable onConfirm) {
        MenuLayout layout = layout(viewer, "confirm-menu");
        if (layout != null) {
            new ConfirmMenu(plugin, viewer, layout, context, onConfirm).open();
        }
    }

    public void openAdmin(Player viewer, int page) {
        MenuLayout layout = layout(viewer, "admin-menu");
        if (layout != null) {
            new AdminMenu(plugin, viewer, layout, page).open();
        }
    }

    public void openAdminPlayer(Player viewer, UUID target) {
        MenuLayout layout = layout(viewer, "admin-player-menu");
        if (layout == null) {
            return;
        }
        plugin.players().loadOffline(target).whenComplete((data, error) -> plugin.scheduler().runAtEntity(viewer, () -> {
            if (error != null) {
                plugin.messages().send(viewer, "errors.database-unavailable");
            } else if (data.isEmpty()) {
                plugin.messages().send(viewer, "errors.player-not-found", Placeholders.of("player", target));
            } else {
                new AdminPlayerMenu(plugin, viewer, layout, data.get()).open();
            }
        }));
    }

    public void openAdminItems(Player viewer, int page) {
        MenuLayout layout = layout(viewer, "admin-items-menu");
        if (layout != null) {
            new AdminItemsMenu(plugin, viewer, layout, page).open();
        }
    }

    public void openAdminBeacons(Player viewer, int page) {
        MenuLayout layout = layout(viewer, "admin-beacons-menu");
        if (layout != null) {
            new AdminBeaconsMenu(plugin, viewer, layout, page).open();
        }
    }

    /** Closes every open LifeCore menu (reload / disable). */
    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.scheduler().runAtEntity(player, () -> {
                if (Menu.openMenu(player) != null) {
                    player.closeInventory();
                }
            });
        }
    }
}
