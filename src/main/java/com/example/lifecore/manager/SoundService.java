package com.example.lifecore.manager;

import com.example.lifecore.util.Guard;
import com.example.lifecore.util.LifeLogger;
import com.example.lifecore.util.compat.Compat;
import com.example.lifecore.util.scheduler.TaskScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Plays configurable sounds from sounds.yml. Sound names are resolved once per reload.
 */
public final class SoundService {

    public record SoundSpec(String key, float volume, float pitch) {
    }

    private final LifeLogger logger;
    private final TaskScheduler scheduler;
    private volatile Map<String, SoundSpec> sounds = Map.of();

    public SoundService(LifeLogger logger, TaskScheduler scheduler) {
        this.logger = logger;
        this.scheduler = scheduler;
    }

    public void load(YamlConfiguration config) {
        Map<String, SoundSpec> parsed = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("sounds");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(id);
                if (entry == null || !entry.getBoolean("enabled", true)) {
                    continue;
                }
                String name = entry.getString("sound", "");
                Optional<String> key = Compat.soundKey(name);
                if (key.isEmpty()) {
                    logger.warn("[sounds] Unknown sound '" + name + "' for '" + id + "' in sounds.yml - it will be silent.");
                    continue;
                }
                float volume = (float) clamp(entry.getDouble("volume", 1.0), 0.0, 10.0);
                float pitch = (float) clamp(entry.getDouble("pitch", 1.0), 0.5, 2.0);
                parsed.put(id, new SoundSpec(key.get(), volume, pitch));
            }
        }
        this.sounds = Collections.unmodifiableMap(parsed);
    }

    public boolean has(String id) {
        return sounds.containsKey(id);
    }

    /** Plays a sound to one player at their own position. Safe from any thread. */
    public void play(Player player, String id) {
        SoundSpec spec = sounds.get(id);
        if (spec == null || player == null) {
            return;
        }
        scheduler.runAtEntity(player, () -> Guard.cosmetic(logger, "sound " + id,
                () -> player.playSound(player, spec.key(), SoundCategory.MASTER, spec.volume(), spec.pitch())));
    }

    /** Plays a sound in the world at a location. Must be called on the thread owning the location. */
    public void playAt(Location location, String id) {
        SoundSpec spec = sounds.get(id);
        World world = location == null ? null : location.getWorld();
        if (spec == null || world == null) {
            return;
        }
        Guard.cosmetic(logger, "sound " + id, () -> world.playSound(location, spec.key(), SoundCategory.MASTER, spec.volume(), spec.pitch()));
    }

    /** Plays a sound to every online player. */
    public void playAll(String id) {
        if (!sounds.containsKey(id)) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            play(player, id);
        }
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
