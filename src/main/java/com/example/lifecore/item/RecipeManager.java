package com.example.lifecore.item;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.api.LifeCoreItemType;
import com.example.lifecore.item.beacon.BeaconTier;
import com.example.lifecore.item.heart.HeartItemDefinition;
import com.example.lifecore.item.scroll.ScrollDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers crafting recipes for heart items, scrolls and beacons.
 * <p>
 * Custom ingredients ({@code lifecore:<id>}) are registered as material choices and verified in
 * {@code PrepareItemCraftEvent}: the matrix must contain exactly the required number of genuine,
 * correctly signed LifeCore items of each id. This keeps working when item names change and stops
 * plain materials (or forged items) from standing in for custom ones.
 */
public final class RecipeManager {

    /**
     * @param resultReference   what the recipe produces ({@code small_heart}, {@code beacon:basic})
     * @param customRequirements custom ingredient id -> required count
     * @param permission         permission required to craft (empty = none)
     * @param uniqueResult       true if every crafted item needs a fresh unique id (beacons)
     */
    public record RecipeInfo(String resultReference, Map<String, Integer> customRequirements, String permission, boolean uniqueResult) {
    }

    private final LifeCorePlugin plugin;
    private final Map<NamespacedKey, RecipeInfo> registered = new ConcurrentHashMap<>();

    public RecipeManager(LifeCorePlugin plugin) {
        this.plugin = plugin;
    }

    public List<String> registerAll() {
        unregisterAll();
        List<String> problems = new ArrayList<>();
        ItemRegistry registry = plugin.items();
        for (HeartItemDefinition heart : registry.hearts()) {
            register("heart_" + heart.id(), heart.id(), heart.recipe(), registry.createHeartItem(heart, 1), false,
                    "items.yml -> heart-items." + heart.id() + ".recipe", problems);
        }
        for (ScrollDefinition scroll : registry.scrolls()) {
            register("scroll_" + scroll.id(), scroll.id(), scroll.recipe(), registry.createScroll(scroll, 1), false,
                    "effects.yml -> scrolls." + scroll.id() + ".recipe", problems);
        }
        for (BeaconTier tier : registry.beaconTiers()) {
            register("beacon_" + tier.id(), ItemRegistry.BEACON_PREFIX + tier.id(), tier.recipe(), registry.createBeacon(tier), true,
                    "beacons.yml -> tiers." + tier.id() + ".recipe", problems);
        }
        return problems;
    }

    private void register(String keyName, String reference, RecipeSpec spec, ItemStack result, boolean unique, String path, List<String> problems) {
        if (!spec.enabled()) {
            return;
        }
        NamespacedKey key = new NamespacedKey(plugin, keyName);
        Map<String, Integer> custom = new HashMap<>();
        Recipe recipe;
        if (spec.shaped()) {
            List<String> shape = spec.shape();
            if (shape.isEmpty() || shape.size() > 3 || shape.stream().anyMatch(row -> row.isEmpty() || row.length() > 3)) {
                problems.add(path + ".shape: must have 1-3 rows of 1-3 characters");
                return;
            }
            ShapedRecipe shaped = new ShapedRecipe(key, result);
            shaped.shape(shape.toArray(new String[0]));
            Map<Character, Integer> occurrences = new HashMap<>();
            for (String row : shape) {
                for (char c : row.toCharArray()) {
                    if (c != ' ') {
                        occurrences.merge(c, 1, Integer::sum);
                    }
                }
            }
            for (Map.Entry<Character, Integer> entry : occurrences.entrySet()) {
                String ingredient = spec.shapedIngredients().get(entry.getKey());
                if (ingredient == null || ingredient.isBlank()) {
                    problems.add(path + ".ingredients: missing ingredient for '" + entry.getKey() + "'");
                    return;
                }
                Material material = resolve(ingredient, custom, entry.getValue(), path, problems);
                if (material == null) {
                    return;
                }
                shaped.setIngredient(entry.getKey(), new RecipeChoice.MaterialChoice(material));
            }
            recipe = shaped;
        } else {
            if (spec.shapelessIngredients().isEmpty() || spec.shapelessIngredients().size() > 9) {
                problems.add(path + ".ingredients: shapeless recipes need 1-9 ingredients");
                return;
            }
            ShapelessRecipe shapeless = new ShapelessRecipe(key, result);
            for (String ingredient : spec.shapelessIngredients()) {
                Material material = resolve(ingredient, custom, 1, path, problems);
                if (material == null) {
                    return;
                }
                shapeless.addIngredient(new RecipeChoice.MaterialChoice(material));
            }
            recipe = shapeless;
        }
        try {
            if (Bukkit.addRecipe(recipe)) {
                registered.put(key, new RecipeInfo(reference, Collections.unmodifiableMap(custom), spec.permission(), unique));
            } else {
                problems.add(path + ": the server rejected the recipe");
            }
        } catch (RuntimeException ex) {
            problems.add(path + ": " + ex.getMessage());
        }
    }

    private @Nullable Material resolve(String ingredient, Map<String, Integer> custom, int count, String path, List<String> problems) {
        if (RecipeSpec.isCustomReference(ingredient)) {
            String reference = ingredient.substring("lifecore:".length()).toLowerCase(Locale.ROOT);
            Optional<Material> material = plugin.items().materialOf(reference);
            if (material.isEmpty()) {
                problems.add(path + ": unknown LifeCore item '" + ingredient + "'");
                return null;
            }
            custom.merge(reference, count, Integer::sum);
            return material.get();
        }
        Material material = Material.matchMaterial(ingredient);
        if (material == null || material.isAir() || !material.isItem()) {
            problems.add(path + ": unknown material '" + ingredient + "'");
            return null;
        }
        return material;
    }

    public void unregisterAll() {
        for (NamespacedKey key : registered.keySet()) {
            try {
                Bukkit.removeRecipe(key);
            } catch (RuntimeException ignored) {
                // server shutting down
            }
        }
        registered.clear();
    }

    public Optional<RecipeInfo> info(NamespacedKey key) {
        return Optional.ofNullable(registered.get(key));
    }

    public boolean isOwn(NamespacedKey key) {
        return registered.containsKey(key);
    }

    public List<NamespacedKey> keys() {
        return new ArrayList<>(registered.keySet());
    }

    /**
     * Verifies the crafting matrix of one of our recipes.
     *
     * @return true if exactly the required custom items (genuine and signed) are present
     */
    public boolean validateMatrix(RecipeInfo info, ItemStack[] matrix) {
        Map<String, Integer> found = new HashMap<>();
        for (ItemStack stack : matrix) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            Optional<ItemIdentity> identity = plugin.items().identify(stack);
            if (identity.isEmpty()) {
                continue;
            }
            ItemIdentity id = identity.get();
            if (!id.valid() || id.type() == null || id.type() == LifeCoreItemType.NOTE) {
                return false;
            }
            String reference = id.type() == LifeCoreItemType.BEACON ? ItemRegistry.BEACON_PREFIX + id.id() : id.id();
            found.merge(reference, 1, Integer::sum);
        }
        return found.equals(info.customRequirements());
    }

    /** @return true if a non-LifeCore recipe tries to consume LifeCore items. */
    public boolean containsCustomItem(ItemStack[] matrix) {
        for (ItemStack stack : matrix) {
            if (plugin.items().isLifeCoreItem(stack)) {
                return true;
            }
        }
        return false;
    }
}
