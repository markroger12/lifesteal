package com.example.lifecore.item.beacon;

import com.example.lifecore.item.ItemTemplate;
import com.example.lifecore.item.RecipeSpec;
import org.bukkit.Material;

import java.util.List;

/**
 * A revive beacon tier from beacons.yml.
 */
public record BeaconTier(
        String id,
        String displayName,
        Material block,
        int durationSeconds,
        double durability,
        double damagePerHit,
        double explosionDamage,
        long cooldownMillis,
        double activationRadius,
        double reviveHearts,
        String permission,
        String particle,
        String soundStart,
        String soundTick,
        String soundDamaged,
        String soundComplete,
        String soundFail,
        List<String> commandsStart,
        List<String> commandsComplete,
        List<String> commandsFail,
        boolean broadcastStart,
        boolean broadcastComplete,
        boolean broadcastFail,
        List<String> hologramLines,
        ItemTemplate template,
        RecipeSpec recipe
) {
}
