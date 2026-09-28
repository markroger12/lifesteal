package com.example.lifecore.item.scroll;

import com.example.lifecore.item.ItemTemplate;
import com.example.lifecore.item.RecipeSpec;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * A sacrificial scroll tier from effects.yml.
 */
public record ScrollDefinition(
        String id,
        double heartCost,
        int durationSeconds,
        List<EffectSpec> effects,
        long cooldownMillis,
        String permission,
        boolean allowElimination,
        String sound,
        String particle,
        String useMessage,
        List<String> commands,
        ItemTemplate template,
        RecipeSpec recipe
) {

    /**
     * @param type  potion effect
     * @param level 1-based level (1 = I)
     * @param name  configured name, used for display
     */
    public record EffectSpec(PotionEffectType type, int level, String name) {
    }
}
