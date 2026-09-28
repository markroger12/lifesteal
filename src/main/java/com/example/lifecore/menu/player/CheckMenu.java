package com.example.lifecore.menu.player;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.menu.Menu;
import com.example.lifecore.menu.MenuLayout;
import com.example.lifecore.model.PlayerData;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;


/**
 * Player lookup GUI (/lifesteal check &lt;player&gt; gui).
 */
public final class CheckMenu extends Menu {

    private final PlayerData target;

    public CheckMenu(LifeCorePlugin plugin, Player viewer, MenuLayout layout, PlayerData target) {
        super(plugin, viewer, layout);
        this.target = target;
    }

    @Override
    protected Placeholders placeholders() {
        return plugin.placeholders().playerPlaceholders(target, Bukkit.getPlayer(target.getUniqueId()))
                .add("viewer", viewer.getName());
    }

    @Override
    protected void build() {
        Placeholders ph = placeholders();
        OfflinePlayer head = Bukkit.getOfflinePlayer(target.getUniqueId());
        for (String key : layout.items().keySet()) {
            placeStatic(key, ph, head, null);
        }
    }
}
