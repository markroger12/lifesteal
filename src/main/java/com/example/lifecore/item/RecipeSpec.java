package com.example.lifecore.item;

import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parsed recipe definition. Ingredients are material names or LifeCore item references
 * ({@code lifecore:<id>} or {@code lifecore:beacon:<tier>}).
 */
public record RecipeSpec(boolean enabled, boolean shaped, List<String> shape, Map<Character, String> shapedIngredients,
                         List<String> shapelessIngredients, String permission) {

    public static final RecipeSpec DISABLED = new RecipeSpec(false, true, List.of(), Map.of(), List.of(), "");

    public static RecipeSpec parse(ConfigurationSection section) {
        if (section == null || !section.getBoolean("enabled", false)) {
            return DISABLED;
        }
        boolean shaped = !section.getString("type", "SHAPED").trim().equalsIgnoreCase("SHAPELESS");
        Map<Character, String> shapedIngredients = new LinkedHashMap<>();
        List<String> shapeless = List.of();
        if (shaped) {
            ConfigurationSection ingredients = section.getConfigurationSection("ingredients");
            if (ingredients != null) {
                for (String key : ingredients.getKeys(false)) {
                    if (key.length() == 1) {
                        shapedIngredients.put(key.charAt(0), ingredients.getString(key, "").trim());
                    }
                }
            }
        } else {
            shapeless = List.copyOf(section.getStringList("ingredients"));
        }
        return new RecipeSpec(true, shaped, List.copyOf(section.getStringList("shape")), Map.copyOf(shapedIngredients),
                shapeless, section.getString("permission", ""));
    }

    public static boolean isCustomReference(String ingredient) {
        return ingredient.toLowerCase(Locale.ROOT).startsWith("lifecore:");
    }
}
