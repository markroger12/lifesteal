package com.example.lifecore.util.compat;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandMap;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Isolates all Minecraft-version dependent lookups.
 * <p>
 * Several Bukkit types changed from enums to interfaces between 1.21 and 1.21.3 and many
 * constants were renamed in 1.20.5. To stay binary compatible from 1.21 through 26.x the plugin
 * never references those constants directly; instead it resolves them through registries by
 * namespaced key, and only ever invokes methods through stable super-interfaces such as {@link Keyed}.
 */
public final class Compat {

    private static final Map<String, String> POTION_ALIASES = new HashMap<>();
    private static final Map<String, String> ENCHANT_ALIASES = new HashMap<>();
    private static final Map<String, String> PARTICLE_ALIASES = new HashMap<>();
    private static final Map<String, Optional<String>> SOUND_CACHE = new ConcurrentHashMap<>();
    private static volatile Map<String, String> soundEnumNames;
    private static volatile Attribute maxHealth;
    private static volatile Method setItemModel;
    private static volatile boolean setItemModelResolved;

    static {
        POTION_ALIASES.put("increase_damage", "strength");
        POTION_ALIASES.put("slow", "slowness");
        POTION_ALIASES.put("fast_digging", "haste");
        POTION_ALIASES.put("slow_digging", "mining_fatigue");
        POTION_ALIASES.put("damage_resistance", "resistance");
        POTION_ALIASES.put("jump", "jump_boost");
        POTION_ALIASES.put("confusion", "nausea");
        POTION_ALIASES.put("heal", "instant_health");
        POTION_ALIASES.put("harm", "instant_damage");

        ENCHANT_ALIASES.put("durability", "unbreaking");
        ENCHANT_ALIASES.put("damage_all", "sharpness");
        ENCHANT_ALIASES.put("damage_undead", "smite");
        ENCHANT_ALIASES.put("damage_arthropods", "bane_of_arthropods");
        ENCHANT_ALIASES.put("protection_environmental", "protection");
        ENCHANT_ALIASES.put("protection_fire", "fire_protection");
        ENCHANT_ALIASES.put("protection_fall", "feather_falling");
        ENCHANT_ALIASES.put("protection_explosions", "blast_protection");
        ENCHANT_ALIASES.put("protection_projectile", "projectile_protection");
        ENCHANT_ALIASES.put("oxygen", "respiration");
        ENCHANT_ALIASES.put("water_worker", "aqua_affinity");
        ENCHANT_ALIASES.put("dig_speed", "efficiency");
        ENCHANT_ALIASES.put("loot_bonus_mobs", "looting");
        ENCHANT_ALIASES.put("loot_bonus_blocks", "fortune");
        ENCHANT_ALIASES.put("arrow_damage", "power");
        ENCHANT_ALIASES.put("arrow_knockback", "punch");
        ENCHANT_ALIASES.put("arrow_fire", "flame");
        ENCHANT_ALIASES.put("arrow_infinite", "infinity");
        ENCHANT_ALIASES.put("luck", "luck_of_the_sea");

        PARTICLE_ALIASES.put("REDSTONE", "DUST");
        PARTICLE_ALIASES.put("VILLAGER_HAPPY", "HAPPY_VILLAGER");
        PARTICLE_ALIASES.put("VILLAGER_ANGRY", "ANGRY_VILLAGER");
        PARTICLE_ALIASES.put("TOTEM", "TOTEM_OF_UNDYING");
        PARTICLE_ALIASES.put("SPELL_WITCH", "WITCH");
        PARTICLE_ALIASES.put("SPELL", "EFFECT");
        PARTICLE_ALIASES.put("SPELL_INSTANT", "INSTANT_EFFECT");
        PARTICLE_ALIASES.put("SPELL_MOB", "ENTITY_EFFECT");
        PARTICLE_ALIASES.put("SMOKE_NORMAL", "SMOKE");
        PARTICLE_ALIASES.put("SMOKE_LARGE", "LARGE_SMOKE");
        PARTICLE_ALIASES.put("FIREWORKS_SPARK", "FIREWORK");
        PARTICLE_ALIASES.put("ENCHANTMENT_TABLE", "ENCHANT");
        PARTICLE_ALIASES.put("EXPLOSION_NORMAL", "POOF");
        PARTICLE_ALIASES.put("EXPLOSION_LARGE", "EXPLOSION");
        PARTICLE_ALIASES.put("EXPLOSION_HUGE", "EXPLOSION_EMITTER");
        PARTICLE_ALIASES.put("DRIP_LAVA", "DRIPPING_LAVA");
        PARTICLE_ALIASES.put("DRIP_WATER", "DRIPPING_WATER");
        PARTICLE_ALIASES.put("CRIT_MAGIC", "ENCHANTED_HIT");
        PARTICLE_ALIASES.put("SUSPENDED_DEPTH", "UNDERWATER");
        PARTICLE_ALIASES.put("WATER_BUBBLE", "BUBBLE");
        PARTICLE_ALIASES.put("WATER_SPLASH", "SPLASH");
        PARTICLE_ALIASES.put("TOWN_AURA", "MYCELIUM");
        PARTICLE_ALIASES.put("SLIME", "ITEM_SLIME");
        PARTICLE_ALIASES.put("SNOWBALL", "ITEM_SNOWBALL");
    }

    private Compat() {
    }

    /**
     * Resolves the max-health attribute. Its key is {@code max_health} since 1.21.2 and
     * {@code generic.max_health} before that.
     */
    public static Attribute maxHealthAttribute() {
        Attribute cached = maxHealth;
        if (cached != null) {
            return cached;
        }
        Attribute resolved = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("max_health"));
        if (resolved == null) {
            resolved = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.max_health"));
        }
        if (resolved == null) {
            throw new IllegalStateException("Unable to resolve the max health attribute on " + Bukkit.getBukkitVersion());
        }
        maxHealth = resolved;
        return resolved;
    }

    public static PotionEffectType potionEffect(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String key = normalizeKey(name);
        key = POTION_ALIASES.getOrDefault(key, key);
        NamespacedKey namespaced = NamespacedKey.fromString(key.contains(":") ? key : "minecraft:" + key);
        return namespaced == null ? null : Registry.EFFECT.get(namespaced);
    }

    public static Enchantment enchantment(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String key = normalizeKey(name);
        key = ENCHANT_ALIASES.getOrDefault(key, key);
        NamespacedKey namespaced = NamespacedKey.fromString(key.contains(":") ? key : "minecraft:" + key);
        return namespaced == null ? null : Registry.ENCHANTMENT.get(namespaced);
    }

    public static Particle particle(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String upper = name.trim().toUpperCase(Locale.ROOT).replace("MINECRAFT:", "").replace('.', '_');
        try {
            return Particle.valueOf(upper);
        } catch (IllegalArgumentException ignored) {
            String alias = PARTICLE_ALIASES.get(upper);
            if (alias != null) {
                try {
                    return Particle.valueOf(alias);
                } catch (IllegalArgumentException ignoredAgain) {
                    return null;
                }
            }
            return null;
        }
    }

    /**
     * Resolves a configured sound into a namespaced sound key usable with the String based
     * playSound overloads. Accepts {@code entity.player.levelup}, {@code minecraft:entity.player.levelup}
     * and legacy enum style names such as {@code ENTITY_PLAYER_LEVELUP}. Custom resource-pack
     * sounds (any namespace) are passed through unchanged.
     */
    public static Optional<String> soundKey(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return SOUND_CACHE.computeIfAbsent(name.trim(), Compat::resolveSound);
    }

    private static Optional<String> resolveSound(String name) {
        if (name.indexOf('.') >= 0 || name.indexOf(':') >= 0) {
            String lower = name.toLowerCase(Locale.ROOT);
            return NamespacedKey.fromString(lower) == null ? Optional.empty() : Optional.of(lower);
        }
        Map<String, String> names = soundEnumNames;
        if (names == null) {
            Map<String, String> built = new HashMap<>();
            try {
                for (Object sound : Registry.SOUNDS) {
                    NamespacedKey key = ((Keyed) sound).getKey();
                    built.put(key.getKey().toUpperCase(Locale.ROOT).replace('.', '_'), key.toString());
                }
            } catch (RuntimeException ignored) {
                // Registry not available (e.g. unit tests without a server) - fall back to heuristics.
            }
            names = built;
            soundEnumNames = names;
        }
        String resolved = names.get(name.toUpperCase(Locale.ROOT));
        if (resolved != null) {
            return Optional.of(resolved);
        }
        return Optional.empty();
    }

    /** @return the upper-case key path of a registry entry, e.g. {@code BLAZE} for {@code minecraft:blaze}. */
    public static String keyName(Object keyed) {
        if (keyed instanceof Keyed k) {
            return k.getKey().getKey().toUpperCase(Locale.ROOT).replace('.', '_');
        }
        return String.valueOf(keyed).toUpperCase(Locale.ROOT);
    }

    /**
     * Sets the 1.21.4+ item model component when the running server supports it.
     *
     * @return true if applied
     */
    public static boolean setItemModel(ItemMeta meta, String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        NamespacedKey namespacedKey = NamespacedKey.fromString(key.toLowerCase(Locale.ROOT));
        if (namespacedKey == null) {
            return false;
        }
        if (!setItemModelResolved) {
            try {
                setItemModel = ItemMeta.class.getMethod("setItemModel", NamespacedKey.class);
            } catch (NoSuchMethodException ignored) {
                setItemModel = null;
            }
            setItemModelResolved = true;
        }
        Method method = setItemModel;
        if (method == null) {
            return false;
        }
        try {
            method.invoke(meta, namespacedKey);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    /** Obtains the server command map on Paper and Spigot. */
    public static CommandMap commandMap() {
        try {
            Method method = Bukkit.getServer().getClass().getMethod("getCommandMap");
            return (CommandMap) method.invoke(Bukkit.getServer());
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    public static boolean classExists(String name) {
        try {
            Class.forName(name, false, Compat.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ex) {
            return false;
        }
    }

    private static String normalizeKey(String name) {
        return name.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
