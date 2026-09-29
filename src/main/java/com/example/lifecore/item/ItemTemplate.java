package com.example.lifecore.item;

import com.example.lifecore.util.compat.Compat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Parsed item appearance (material, name, lore, model data, glow, enchantments, flags, head skin).
 *
 * @param headTexture custom skin; when set the item is always a {@link Material#PLAYER_HEAD}
 */
public record ItemTemplate(Material material, String name, List<String> lore, int customModelData, String itemModel,
                           boolean glow, Map<Enchantment, Integer> enchantments, List<ItemFlag> flags,
                           @Nullable HeadTexture headTexture) {

    /**
     * Parses an item section. Problems are reported to the consumer and replaced by safe defaults.
     */
    public static ItemTemplate parse(ConfigurationSection section, Material fallback, Consumer<String> problems) {
        if (section == null) {
            return new ItemTemplate(fallback, "", List.of(), 0, "", false, Map.of(), List.of(), null);
        }
        String materialName = section.getString("material", fallback.name());
        Material material = Material.matchMaterial(materialName);
        if (material == null || material.isAir() || !material.isItem()) {
            problems.accept("unknown or invalid material '" + materialName + "', using " + fallback.name());
            material = fallback;
        }
        Map<Enchantment, Integer> enchantments = new LinkedHashMap<>();
        for (String entry : section.getStringList("enchantments")) {
            String[] parts = entry.split(":");
            Enchantment enchantment = Compat.enchantment(parts[0]);
            if (enchantment == null) {
                problems.accept("unknown enchantment '" + parts[0] + "'");
                continue;
            }
            int level = 1;
            if (parts.length > 1) {
                try {
                    level = Math.max(1, Math.min(255, Integer.parseInt(parts[1].trim())));
                } catch (NumberFormatException ex) {
                    problems.accept("invalid enchantment level in '" + entry + "'");
                }
            }
            enchantments.put(enchantment, level);
        }
        List<ItemFlag> flags = new ArrayList<>();
        for (String flag : section.getStringList("flags")) {
            try {
                flags.add(ItemFlag.valueOf(flag.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                problems.accept("unknown item flag '" + flag + "'");
            }
        }
        HeadTexture head = parseHead(section.getString("head-texture", ""), problems);
        if (head != null) {
            material = Material.PLAYER_HEAD;
        }
        int cmd = section.getInt("custom-model-data", 0);
        return new ItemTemplate(material, section.getString("name", ""), List.copyOf(section.getStringList("lore")),
                Math.max(0, cmd), section.getString("item-model", ""), section.getBoolean("glow", false),
                Collections.unmodifiableMap(enchantments), List.copyOf(flags), head);
    }

    private static @Nullable HeadTexture parseHead(String input, Consumer<String> problems) {
        if (input == null || input.isBlank()) {
            return null;
        }
        HeadTexture head = HeadTexture.parse(input).orElse(null);
        if (head == null) {
            problems.accept("head-texture: no skin found - paste the head's texture Value or its textures.minecraft.net URL");
            return null;
        }
        // Check once at load that this server accepts the profile, instead of failing on every item.
        if (Bukkit.getServer() != null && new ItemStack(Material.PLAYER_HEAD).getItemMeta() instanceof SkullMeta probe
                && !head.apply(probe)) {
            problems.accept("head-texture: this server rejected the skin, the item is shown without it");
            return null;
        }
        return head;
    }

    /**
     * Builds the (unsigned) item. {@code formatter} turns raw text into coloured text;
     * a lore line equal to a key of {@code multiLine} is replaced by that list.
     */
    public ItemStack build(int amount, UnaryOperator<String> formatter, Map<String, List<String>> multiLine) {
        ItemStack stack = new ItemStack(material, Math.max(1, Math.min(amount, material.getMaxStackSize())));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        if (!name.isEmpty()) {
            meta.setDisplayName(formatter.apply(name));
        }
        if (!lore.isEmpty()) {
            List<String> lines = new ArrayList<>();
            for (String line : lore) {
                List<String> expansion = multiLine.get(line.trim());
                if (expansion != null) {
                    lines.addAll(expansion);
                } else {
                    lines.add(formatter.apply(line));
                }
            }
            meta.setLore(lines);
        }
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }
        if (!itemModel.isBlank()) {
            Compat.setItemModel(meta, itemModel);
        }
        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            meta.addEnchant(entry.getKey(), entry.getValue(), true);
        }
        if (glow) {
            meta.setEnchantmentGlintOverride(true);
        }
        if (!flags.isEmpty()) {
            meta.addItemFlags(flags.toArray(new ItemFlag[0]));
        }
        if (headTexture != null && meta instanceof SkullMeta skull) {
            headTexture.apply(skull);
        }
        stack.setItemMeta(meta);
        return stack;
    }
}
