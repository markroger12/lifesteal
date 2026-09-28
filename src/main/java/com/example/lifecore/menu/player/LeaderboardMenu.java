package com.example.lifecore.menu.player;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.api.LeaderboardEntry;
import com.example.lifecore.api.LeaderboardType;
import com.example.lifecore.menu.MenuItemSpec;
import com.example.lifecore.menu.MenuLayout;
import com.example.lifecore.menu.PaginatedMenu;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;

/**
 * Leaderboard GUI with category buttons (hearts, kills, deaths, revives).
 */
public final class LeaderboardMenu extends PaginatedMenu<LeaderboardEntry> {

    private LeaderboardType type;

    public LeaderboardMenu(LifeCorePlugin plugin, Player viewer, MenuLayout layout, LeaderboardType type, int page) {
        super(plugin, viewer, layout, page);
        this.type = type;
    }

    @Override
    protected List<LeaderboardEntry> entries() {
        return plugin.leaderboards().get(type);
    }

    @Override
    protected Set<String> specialItems() {
        return Set.of("hearts", "kills", "deaths", "revives");
    }

    @Override
    protected Placeholders placeholders() {
        return super.placeholders().add("type", plugin.messages().raw("leaderboard.types." + type.id()));
    }

    @Override
    protected void build() {
        super.build();
        Placeholders ph = placeholders();
        for (LeaderboardType category : LeaderboardType.values()) {
            placeStatic(category.id(), ph, null, click -> {
                type = category;
                page = 0;
                refresh();
            });
        }
    }

    @Override
    protected void placeEntry(int slot, LeaderboardEntry entry) {
        MenuItemSpec template = layout.entry();
        if (template == null) {
            return;
        }
        Placeholders ph = placeholders()
                .add("position", entry.position())
                .add("name", entry.name())
                .add("value", type == LeaderboardType.HEARTS ? plugin.messages().hearts(entry.value()) : String.valueOf((long) entry.value()));
        set(slot, plugin.menus().items().build(template, viewer, ph, Bukkit.getOfflinePlayer(entry.uuid())), null);
    }
}
