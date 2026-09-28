package com.example.lifecore.menu;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A configurable menu item (menus.yml).
 *
 * @param slots           inventory slots (may be empty for templates)
 * @param material        item material
 * @param name            display name
 * @param lore            lore lines
 * @param glow            enchantment glint
 * @param customModelData resource pack model data (0 = none)
 * @param skullOwner      player name placeholder for PLAYER_HEAD items (empty = none)
 * @param amount          stack size
 * @param actions         click actions ([close], [open] menu, [player] cmd, [console] cmd, ...)
 * @param permission      viewer permission required to see the item (empty = everyone)
 */
public record MenuItemSpec(List<Integer> slots, Material material, String name, List<String> lore, boolean glow,
                           int customModelData, String skullOwner, int amount, List<String> actions, String permission) {

    public static MenuItemSpec parse(ConfigurationSection section, int rows, Consumer<String> problems) {
        String materialName = section.getString("material", "STONE");
        Material material = Material.matchMaterial(materialName);
        if (material == null || material.isAir() || !material.isItem()) {
            problems.accept("unknown material '" + materialName + "', using STONE");
            material = Material.STONE;
        }
        List<Integer> slots = new ArrayList<>();
        if (section.contains("slot")) {
            slots.add(section.getInt("slot"));
        }
        if (section.contains("slots")) {
            Object raw = section.get("slots");
            if (raw instanceof List<?>) {
                for (String part : section.getStringList("slots")) {
                    slots.addAll(parseSlots(part));
                }
            } else {
                slots.addAll(parseSlots(section.getString("slots", "")));
            }
        }
        int size = rows * 9;
        slots.removeIf(slot -> {
            if (slot < 0 || slot >= size) {
                problems.accept("slot " + slot + " is outside the menu (0-" + (size - 1) + ")");
                return true;
            }
            return false;
        });
        return new MenuItemSpec(List.copyOf(slots), material, section.getString("name", ""),
                List.copyOf(section.getStringList("lore")), section.getBoolean("glow", false),
                Math.max(0, section.getInt("custom-model-data", 0)), section.getString("skull-owner", ""),
                Math.max(1, Math.min(64, section.getInt("amount", 1))), List.copyOf(section.getStringList("actions")),
                section.getString("permission", ""));
    }

    /** Parses "10", "10-16" or "10-16,19-25" into slot numbers. */
    public static List<Integer> parseSlots(String text) {
        List<Integer> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        for (String part : text.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                int dash = trimmed.indexOf('-');
                if (dash > 0) {
                    int from = Integer.parseInt(trimmed.substring(0, dash).trim());
                    int to = Integer.parseInt(trimmed.substring(dash + 1).trim());
                    for (int i = Math.min(from, to); i <= Math.max(from, to) && i < 54; i++) {
                        out.add(i);
                    }
                } else {
                    out.add(Integer.parseInt(trimmed));
                }
            } catch (NumberFormatException ignored) {
                // invalid part - skipped
            }
        }
        return out;
    }

    public int firstSlot() {
        return slots.isEmpty() ? -1 : slots.get(0);
    }
}
