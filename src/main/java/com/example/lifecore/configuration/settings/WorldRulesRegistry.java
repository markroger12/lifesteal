package com.example.lifecore.configuration.settings;

import com.example.lifecore.configuration.ConfigReader;
import org.bukkit.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves {@link WorldRules} per world from worlds.yml. Results are cached per world name.
 */
public final class WorldRulesRegistry {

    private final WorldRules defaults;
    private final Map<String, WorldRules> explicit;
    private final Map<String, WorldRules> cache = new ConcurrentHashMap<>();

    public WorldRulesRegistry(ConfigReader r) {
        this.defaults = read(r.child("default"), WorldRules.DEFAULT);
        Map<String, WorldRules> worlds = new HashMap<>();
        for (String world : r.keys("worlds")) {
            worlds.put(world.toLowerCase(Locale.ROOT), read(r.child("worlds." + world), defaults));
        }
        apply(worlds, lower(r.getStringList("disabled-worlds")), rules -> copy(rules, false, rules.safe(), rules.heartLoss(), rules.instantElimination()));
        apply(worlds, lower(r.getStringList("safe-worlds")), rules -> copy(rules, rules.enabled(), true, rules.heartLoss(), rules.instantElimination()));
        apply(worlds, lower(r.getStringList("no-heart-loss-worlds")), rules -> copy(rules, rules.enabled(), rules.safe(), false, rules.instantElimination()));
        apply(worlds, lower(r.getStringList("instant-elimination-worlds")), rules -> copy(rules, rules.enabled(), rules.safe(), rules.heartLoss(), true));
        this.explicit = Map.copyOf(worlds);
    }

    public WorldRules get(World world) {
        return world == null ? defaults : get(world.getName());
    }

    public WorldRules get(String worldName) {
        if (worldName == null) {
            return defaults;
        }
        return cache.computeIfAbsent(worldName.toLowerCase(Locale.ROOT), key -> explicit.getOrDefault(key, defaults));
    }

    public WorldRules defaults() {
        return defaults;
    }

    private void apply(Map<String, WorldRules> worlds, Set<String> names, java.util.function.UnaryOperator<WorldRules> change) {
        for (String name : names) {
            worlds.put(name, change.apply(worlds.getOrDefault(name, defaults)));
        }
    }

    private static Set<String> lower(List<String> list) {
        Set<String> out = new HashSet<>();
        for (String entry : list) {
            out.add(entry.toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private static WorldRules copy(WorldRules r, boolean enabled, boolean safe, boolean heartLoss, boolean instant) {
        return new WorldRules(enabled, safe, heartLoss, r.playerKills(), r.heartGain(), r.mobLoss(), r.elimination(),
                instant, r.heartDrops(), r.items(), r.beacons(), r.killerGain(), r.victimLoss(), r.naturalLossMultiplier());
    }

    private static WorldRules read(ConfigReader r, WorldRules parent) {
        return new WorldRules(
                r.getBoolean("enabled", parent.enabled()),
                r.getBoolean("safe", parent.safe()),
                r.getBoolean("heart-loss", parent.heartLoss()),
                r.getBoolean("player-kills", parent.playerKills()),
                r.getBoolean("heart-gain", parent.heartGain()),
                r.getBoolean("mob-loss", parent.mobLoss()),
                r.getBoolean("elimination", parent.elimination()),
                r.getBoolean("instant-elimination", parent.instantElimination()),
                r.getBoolean("heart-drops", parent.heartDrops()),
                r.getBoolean("items", parent.items()),
                r.getBoolean("beacons", parent.beacons()),
                r.getDouble("killer-gain", parent.killerGain(), -1, LifeCoreSettings.ABSOLUTE_MAX_HEARTS),
                r.getDouble("victim-loss", parent.victimLoss(), -1, LifeCoreSettings.ABSOLUTE_MAX_HEARTS),
                r.getDouble("natural-loss-multiplier", parent.naturalLossMultiplier(), 0, 100));
    }
}
