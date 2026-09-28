package com.example.lifecore.menu;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.entity.Player;

import java.util.Set;

/**
 * Generic yes/no confirmation menu.
 */
public final class ConfirmMenu extends Menu {

    private final Placeholders context;
    private final Runnable onConfirm;
    private boolean decided;

    public ConfirmMenu(LifeCorePlugin plugin, Player viewer, MenuLayout layout, Placeholders context, Runnable onConfirm) {
        super(plugin, viewer, layout);
        this.context = context;
        this.onConfirm = onConfirm;
    }

    @Override
    protected Placeholders placeholders() {
        return super.placeholders().addAll(context);
    }

    @Override
    protected void build() {
        Placeholders ph = placeholders();
        placeAllStatic(ph, Set.of("confirm", "cancel"));
        placeStatic("confirm", ph, null, click -> {
            if (decided) {
                return;
            }
            decided = true;
            viewer.closeInventory();
            onConfirm.run();
        });
        placeStatic("cancel", ph, null, click -> {
            decided = true;
            viewer.closeInventory();
            plugin.messages().send(viewer, "menus.cancelled");
        });
    }
}
