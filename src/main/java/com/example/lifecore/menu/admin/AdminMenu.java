package com.example.lifecore.menu.admin;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.menu.MenuItemSpec;
import com.example.lifecore.menu.MenuLayout;
import com.example.lifecore.menu.PaginatedMenu;
import com.example.lifecore.model.PlayerData;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Admin overview: online players, search, item & beacon management and reload.
 */
public final class AdminMenu extends PaginatedMenu<Player> {

    private final List<Player> players;

    public AdminMenu(LifeCorePlugin plugin, Player viewer, MenuLayout layout, int page) {
        super(plugin, viewer, layout, page);
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.sort(Comparator.comparing(p -> p.getName().toLowerCase(java.util.Locale.ROOT)));
        this.players = online;
    }

    @Override
    protected List<Player> entries() {
        return players;
    }

    @Override
    protected Set<String> specialItems() {
        return Set.of("search", "items", "beacons", "eliminated", "reload");
    }

    @Override
    protected Placeholders placeholders() {
        return super.placeholders()
                .add("online", players.size())
                .add("beacons", plugin.beacons().active().size())
                .add("cached", plugin.players().cached().size());
    }

    @Override
    protected void build() {
        super.build();
        Placeholders ph = placeholders();
        placeStatic("search", ph, null, click -> plugin.chatInput().request(viewer, "chat-input.search-player", 30,
                input -> plugin.players().resolve(input).thenAccept(target -> plugin.scheduler().runAtEntity(viewer, () -> {
                    if (target.isEmpty()) {
                        plugin.messages().send(viewer, "errors.player-not-found", Placeholders.of("player", input));
                        return;
                    }
                    plugin.menus().openAdminPlayer(viewer, target.get().uuid());
                }))));
        placeStatic("items", ph, null, click -> plugin.menus().openAdminItems(viewer, 0));
        placeStatic("beacons", ph, null, click -> plugin.menus().openAdminBeacons(viewer, 0));
        placeStatic("eliminated", ph, null, click -> plugin.menus().openReviveMenu(viewer, 0));
        placeStatic("reload", ph, null, click -> {
            viewer.closeInventory();
            plugin.reloadAll(viewer);
        });
    }

    @Override
    protected void placeEntry(int slot, Player entry) {
        MenuItemSpec template = layout.entry();
        if (template == null) {
            return;
        }
        PlayerData data = plugin.players().get(entry);
        Placeholders ph = data == null ? Placeholders.of("player", entry.getName())
                : plugin.placeholders().playerPlaceholders(data, entry);
        set(slot, plugin.menus().items().build(template, viewer, ph, entry), click -> {
            plugin.sounds().play(viewer, "menu-click");
            plugin.menus().openAdminPlayer(viewer, entry.getUniqueId());
        });
    }
}
