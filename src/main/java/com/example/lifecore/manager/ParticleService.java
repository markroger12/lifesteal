package com.example.lifecore.manager;

import com.example.lifecore.util.Guard;
import com.example.lifecore.util.LifeLogger;
import com.example.lifecore.util.compat.Compat;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lightweight particle effects from effects.yml. Effects are pre-parsed on reload and spawn a
 * bounded number of particles (POINT = one burst, RING = a small circle), keeping the cost
 * predictable on large servers.
 */
public final class ParticleService {

    public enum Shape {POINT, RING}

    public record Effect(Particle particle, int count, double offsetX, double offsetY, double offsetZ, double speed,
                         double yOffset, Shape shape, int points, double radius, Object data) {
    }

    private static final int MAX_COUNT = 200;
    private static final int MAX_POINTS = 64;

    private final LifeLogger logger;
    private volatile boolean enabled = true;
    private volatile Map<String, Effect> effects = Map.of();

    public ParticleService(LifeLogger logger) {
        this.logger = logger;
    }

    public void load(YamlConfiguration config) {
        this.enabled = config.getBoolean("particles.enabled", true);
        Map<String, Effect> parsed = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("particles.effects");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(id);
                if (entry == null || !entry.getBoolean("enabled", true)) {
                    continue;
                }
                Effect effect = parse(id, entry);
                if (effect != null) {
                    parsed.put(id, effect);
                }
            }
        }
        this.effects = Collections.unmodifiableMap(parsed);
    }

    private Effect parse(String id, ConfigurationSection entry) {
        String name = entry.getString("particle", "");
        Particle particle = Compat.particle(name);
        if (particle == null) {
            logger.warn("[effects] Unknown particle '" + name + "' for effect '" + id + "' - effect disabled.");
            return null;
        }
        Object data = null;
        Class<?> dataType = particle.getDataType();
        if (dataType == Particle.DustOptions.class) {
            data = new Particle.DustOptions(parseColor(entry.getString("color", "#FF0000")), (float) Math.max(0.1, Math.min(4.0, entry.getDouble("size", 1.0))));
        } else if (dataType == Color.class) {
            data = parseColor(entry.getString("color", "#FFFFFF"));
        } else if (dataType != Void.class) {
            logger.warn("[effects] Particle '" + name + "' (effect '" + id + "') needs data of type " + dataType.getSimpleName()
                    + " which is not supported - effect disabled.");
            return null;
        }
        List<Double> offset = entry.getDoubleList("offset");
        double ox = offset.size() > 0 ? offset.get(0) : 0.0;
        double oy = offset.size() > 1 ? offset.get(1) : 0.0;
        double oz = offset.size() > 2 ? offset.get(2) : 0.0;
        Shape shape;
        try {
            shape = Shape.valueOf(entry.getString("shape", "POINT").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            shape = Shape.POINT;
        }
        return new Effect(particle, clampInt(entry.getInt("count", 5), 0, MAX_COUNT), ox, oy, oz,
                entry.getDouble("speed", 0.0), entry.getDouble("y-offset", 1.0), shape,
                clampInt(entry.getInt("points", 12), 1, MAX_POINTS), Math.max(0.1, Math.min(5.0, entry.getDouble("radius", 1.0))), data);
    }

    /** Spawns an effect. Must be called on the thread owning the location. */
    public void play(String id, Location base) {
        if (!enabled || base == null || id == null || id.isEmpty()) {
            return;
        }
        Effect effect = effects.get(id);
        World world = base.getWorld();
        if (effect == null || world == null) {
            return;
        }
        Guard.cosmetic(logger, "particle " + id, () -> spawn(effect, world, base));
    }

    private void spawn(Effect effect, World world, Location base) {
        double y = base.getY() + effect.yOffset();
        if (effect.shape() == Shape.RING) {
            for (int i = 0; i < effect.points(); i++) {
                double angle = 2 * Math.PI * i / effect.points();
                world.spawnParticle(effect.particle(), base.getX() + Math.cos(angle) * effect.radius(), y,
                        base.getZ() + Math.sin(angle) * effect.radius(), Math.max(1, effect.count()),
                        effect.offsetX(), effect.offsetY(), effect.offsetZ(), effect.speed(), effect.data());
            }
        } else {
            world.spawnParticle(effect.particle(), base.getX(), y, base.getZ(), effect.count(),
                    effect.offsetX(), effect.offsetY(), effect.offsetZ(), effect.speed(), effect.data());
        }
    }

    public boolean has(String id) {
        return effects.containsKey(id);
    }

    private static Color parseColor(String hex) {
        String value = hex == null ? "" : hex.trim().replace("#", "");
        try {
            return Color.fromRGB(Integer.parseInt(value, 16) & 0xFFFFFF);
        } catch (NumberFormatException ex) {
            return Color.RED;
        }
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
