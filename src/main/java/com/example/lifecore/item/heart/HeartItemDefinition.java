package com.example.lifecore.item.heart;

import com.example.lifecore.configuration.settings.LifeCoreSettings;
import com.example.lifecore.item.ItemTemplate;
import com.example.lifecore.item.RecipeSpec;

import java.util.List;

/**
 * A consumable heart item tier from items.yml.
 */
public record HeartItemDefinition(
        String id,
        double hearts,
        long cooldownMillis,
        String permission,
        AtMaxBehavior atMaxHearts,
        int absorptionSeconds,
        List<String> atMaxCommands,
        List<String> commands,
        String sound,
        String particle,
        ItemTemplate template,
        DropSpec drop,
        RecipeSpec recipe
) {

    public enum AtMaxBehavior {DENY, CONSUME, ABSORPTION, COMMANDS}

    public record DropSpec(boolean enabled, double chance, int amount, LifeCoreSettings.DropBehavior behavior) {
        public static final DropSpec DISABLED = new DropSpec(false, 0, 0, LifeCoreSettings.DropBehavior.GROUND);
    }
}
