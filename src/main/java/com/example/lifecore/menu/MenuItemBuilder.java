package com.example.lifecore.menu;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds menu items from {@link MenuItemSpec}s, applying placeholders, PlaceholderAPI and colours.
 */
public final class MenuItemBuilder {

    private final LifeCorePlugin plugin;

    public MenuItemBuilder(LifeCorePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * @param head owner for PLAYER_HEAD items (overrides skull-owner)
     */
    public ItemStack build(MenuItemSpec spec, Player viewer, Placeholders placeholders, @Nullable OfflinePlayer head) {
        ItemStack stack = new ItemStack(spec.material(), spec.amount());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        OfflinePlayer context = head != null ? head : viewer;
        if (!spec.name().isEmpty()) {
            meta.setDisplayName(plugin.messages().formatItemText(context, spec.name(), placeholders));
        } else {
            meta.setDisplayName(" ");
        }
        if (!spec.lore().isEmpty()) {
            List<String> lore = new ArrayList<>();
            for (String line : spec.lore()) {
                String formatted = plugin.messages().formatItemText(context, line, placeholders);
                for (String split : formatted.split("\n")) {
                    lore.add(split);
                }
            }
            meta.setLore(lore);
        }
        if (spec.customModelData() > 0) {
            meta.setCustomModelData(spec.customModelData());
        }
        if (spec.glow()) {
            meta.setEnchantmentGlintOverride(true);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        if (meta instanceof SkullMeta skull && spec.material() == Material.PLAYER_HEAD) {
            OfflinePlayer owner = head;
            if (owner == null && !spec.skullOwner().isEmpty()) {
                String name = placeholders.apply(spec.skullOwner());
                Player online = Bukkit.getPlayerExact(name);
                owner = online != null ? online : null;
            }
            if (owner != null) {
                skull.setOwningPlayer(owner);
            }
        }
        stack.setItemMeta(meta);
        return stack;
    }
}
