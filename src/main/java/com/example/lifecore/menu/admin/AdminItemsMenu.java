package com.example.lifecore.menu.admin;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.manager.heart.HeartMath;
import com.example.lifecore.menu.MenuItemSpec;
import com.example.lifecore.menu.MenuLayout;
import com.example.lifecore.menu.PaginatedMenu;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Lets admins take any LifeCore item (heart items, scrolls, beacons, heart notes).
 */
public final class AdminItemsMenu extends PaginatedMenu<String> {

    private final List<String> references;

    public AdminItemsMenu(LifeCorePlugin plugin, Player viewer, MenuLayout layout, int page) {
        super(plugin, viewer, layout, page);
        this.references = plugin.items().giveableIds();
    }

    @Override
    protected List<String> entries() {
        return references;
    }

    @Override
    protected Set<String> specialItems() {
        return Set.of("note", "back");
    }

    @Override
    protected void build() {
        super.build();
        Placeholders ph = placeholders();
        placeStatic("note", ph, null, click -> plugin.chatInput().request(viewer, "chat-input.enter-note-hearts", 30, input -> {
            OptionalDouble value = HeartMath.parse(input, plugin.settings().hearts.hardLimit(), false, plugin.settings().hearts.halfHearts());
            if (value.isEmpty()) {
                plugin.messages().send(viewer, "errors.invalid-amount", Placeholders.of("input", input,
                        "max", plugin.messages().hearts(plugin.settings().hearts.hardLimit())));
                return;
            }
            plugin.itemUse().giveOrDrop(viewer, plugin.notes().createAdminNote(value.getAsDouble(), viewer.getUniqueId(), viewer.getName()));
            plugin.messages().send(viewer, "admin.item-given", Placeholders.of("item", "note", "amount", 1, "player", viewer.getName()));
        }));
        placeStatic("back", ph, null, click -> plugin.menus().openAdmin(viewer, 0));
    }

    @Override
    protected void placeEntry(int slot, String reference) {
        Optional<ItemStack> preview = plugin.items().create(reference, 1);
        if (preview.isEmpty()) {
            return;
        }
        ItemStack display = preview.get().clone();
        MenuItemSpec template = layout.entry();
        if (template != null && !template.lore().isEmpty()) {
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
                Placeholders ph = Placeholders.of("id", reference);
                for (String line : template.lore()) {
                    lore.add(plugin.messages().formatItemText(viewer, line, ph));
                }
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
        }
        set(slot, display, click -> give(reference, click));
    }

    private void give(String reference, ClickType click) {
        int amount = click.isShiftClick() ? 16 : 1;
        int given = 0;
        for (int i = 0; i < amount; ) {
            Optional<ItemStack> item = plugin.items().create(reference, amount - i);
            if (item.isEmpty()) {
                break;
            }
            plugin.itemUse().giveOrDrop(viewer, item.get());
            given += item.get().getAmount();
            i += item.get().getAmount();
        }
        plugin.sounds().play(viewer, "menu-click");
        plugin.messages().send(viewer, "admin.item-given", Placeholders.of("item", reference, "amount", given, "player", viewer.getName()));
        plugin.log().info("admin", "Item given via menu", "admin", viewer.getName(), "item", reference, "amount", given);
    }
}
