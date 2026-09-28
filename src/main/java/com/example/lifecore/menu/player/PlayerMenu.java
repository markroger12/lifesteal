package com.example.lifecore.menu.player;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.menu.Menu;
import com.example.lifecore.menu.MenuLayout;
import com.example.lifecore.model.PlayerData;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.entity.Player;

import java.util.Set;

/**
 * The main player menu (/lifesteal menu). Fully driven by menus.yml.
 */
public final class PlayerMenu extends Menu {

    public PlayerMenu(LifeCorePlugin plugin, Player viewer, MenuLayout layout) {
        super(plugin, viewer, layout);
    }

    @Override
    protected Placeholders placeholders() {
        PlayerData data = plugin.players().get(viewer);
        Placeholders ph = data == null ? super.placeholders() : plugin.placeholders().playerPlaceholders(data, viewer);
        return ph.add("revive_cost", plugin.messages().hearts(plugin.settings().revive.playerRevive().costHearts()))
                .add("withdraw_min", plugin.messages().hearts(plugin.settings().withdraw.minAmount()));
    }

    @Override
    protected void build() {
        Placeholders ph = placeholders();
        placeAllStatic(ph, Set.of("profile"));
        placeStatic("profile", ph, viewer, null);
    }
}
