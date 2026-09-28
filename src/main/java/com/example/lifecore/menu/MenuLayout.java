package com.example.lifecore.menu;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Layout of one menu from menus.yml.
 *
 * @param id           menu id (section name)
 * @param title        inventory title
 * @param rows         1-6
 * @param filler       item filling empty slots (null = none)
 * @param items        named static items
 * @param contentSlots slots for dynamic entries (paginated menus)
 * @param entry        template for dynamic entries (null for static menus)
 * @param templates    extra named templates (e.g. per-category items)
 */
public record MenuLayout(String id, String title, int rows, @Nullable MenuItemSpec filler, Map<String, MenuItemSpec> items,
                         List<Integer> contentSlots, @Nullable MenuItemSpec entry, Map<String, MenuItemSpec> templates) {

    public static MenuLayout parse(String id, ConfigurationSection section, Consumer<String> problems) {
        int rows = Math.max(1, Math.min(6, section.getInt("rows", 6)));
        MenuItemSpec filler = null;
        ConfigurationSection fillerSection = section.getConfigurationSection("filler");
        if (fillerSection != null && fillerSection.getBoolean("enabled", true)) {
            filler = MenuItemSpec.parse(fillerSection, rows, p -> problems.accept(id + ".filler: " + p));
        }
        Map<String, MenuItemSpec> items = new LinkedHashMap<>();
        ConfigurationSection itemSection = section.getConfigurationSection("items");
        if (itemSection != null) {
            for (String key : itemSection.getKeys(false)) {
                ConfigurationSection child = itemSection.getConfigurationSection(key);
                if (child != null) {
                    items.put(key, MenuItemSpec.parse(child, rows, p -> problems.accept(id + ".items." + key + ": " + p)));
                }
            }
        }
        List<Integer> content = new ArrayList<>(MenuItemSpec.parseSlots(section.getString("content-slots", "")));
        content.removeIf(slot -> slot >= rows * 9);
        MenuItemSpec entry = null;
        ConfigurationSection entrySection = section.getConfigurationSection("entry");
        if (entrySection != null) {
            entry = MenuItemSpec.parse(entrySection, rows, p -> problems.accept(id + ".entry: " + p));
        }
        Map<String, MenuItemSpec> templates = new LinkedHashMap<>();
        ConfigurationSection templateSection = section.getConfigurationSection("templates");
        if (templateSection != null) {
            for (String key : templateSection.getKeys(false)) {
                ConfigurationSection child = templateSection.getConfigurationSection(key);
                if (child != null) {
                    templates.put(key, MenuItemSpec.parse(child, rows, p -> problems.accept(id + ".templates." + key + ": " + p)));
                }
            }
        }
        return new MenuLayout(id, section.getString("title", id), rows, filler, Collections.unmodifiableMap(items),
                List.copyOf(content), entry, Collections.unmodifiableMap(templates));
    }

    public @Nullable MenuItemSpec item(String key) {
        return items.get(key);
    }

    public @Nullable MenuItemSpec template(String key) {
        return templates.get(key);
    }
}
