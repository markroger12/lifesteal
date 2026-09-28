package com.example.lifecore.configuration;

import com.example.lifecore.util.LifeLogger;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Loads (and migrates) every LifeCore configuration file.
 */
public final class ConfigManager {

    public static final List<String> FILES = List.of("config.yml", "messages.yml", "items.yml", "beacons.yml",
            "effects.yml", "worlds.yml", "sounds.yml", "menus.yml", "storage.yml");

    private final ConfigMigrationManager migrationManager;
    private final LifeLogger logger;
    private final Map<String, YamlConfiguration> configs = new ConcurrentHashMap<>();
    private final Map<String, YamlConfiguration> defaults = new ConcurrentHashMap<>();

    public ConfigManager(File dataFolder, Function<String, InputStream> resourceProvider, LifeLogger logger) {
        this.logger = logger;
        this.migrationManager = new ConfigMigrationManager(dataFolder, resourceProvider, logger);
    }

    /** Result of a (re)load: which files failed and why. */
    public record LoadResult(List<String> brokenFiles, List<String> migratedFiles) {
        public boolean success() {
            return brokenFiles.isEmpty();
        }
    }

    /**
     * Loads all files. Broken files fall back to the bundled defaults and are reported,
     * so the plugin always has a usable configuration.
     */
    public LoadResult loadAll() throws IOException {
        List<String> broken = new ArrayList<>();
        List<String> migrated = new ArrayList<>();
        for (String file : FILES) {
            ConfigMigrationManager.Outcome outcome = migrationManager.process(file);
            YamlConfiguration config = outcome.config();
            config.setDefaults(outcome.defaults());
            configs.put(file, config);
            defaults.put(file, outcome.defaults());
            if (outcome.state() == ConfigMigrationManager.State.BROKEN) {
                broken.add(file + " (" + outcome.error().lines().findFirst().orElse("invalid YAML") + ")");
            } else if (outcome.state() == ConfigMigrationManager.State.MIGRATED) {
                migrated.add(file);
            } else if (outcome.state() == ConfigMigrationManager.State.CREATED) {
                logger.info("[config] Created default " + file);
            }
        }
        return new LoadResult(broken, migrated);
    }

    public YamlConfiguration get(String file) {
        YamlConfiguration config = configs.get(file);
        if (config == null) {
            throw new IllegalStateException(file + " has not been loaded");
        }
        return config;
    }

    public YamlConfiguration defaults(String file) {
        return defaults.get(file);
    }

    public ConfigMigrationManager migrationManager() {
        return migrationManager;
    }
}
