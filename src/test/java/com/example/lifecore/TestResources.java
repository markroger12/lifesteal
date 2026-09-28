package com.example.lifecore;

import com.example.lifecore.configuration.ConfigReader;
import com.example.lifecore.configuration.settings.LifeCoreSettings;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Loads the bundled default configuration files for tests.
 */
public final class TestResources {

    private TestResources() {
    }

    public static InputStream open(String name) {
        return TestResources.class.getClassLoader().getResourceAsStream(name);
    }

    public static YamlConfiguration yaml(String name) {
        YamlConfiguration config = new YamlConfiguration();
        try (InputStream in = open(name); Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            config.load(reader);
        } catch (IOException | InvalidConfigurationException ex) {
            throw new IllegalStateException("Cannot load " + name, ex);
        }
        return config;
    }

    public static LifeCoreSettings defaultSettings() {
        return new LifeCoreSettings(new ConfigReader(yaml("config.yml"), "config.yml"));
    }

    public static LifeCoreSettings settings(String extraYaml) {
        YamlConfiguration config = yaml("config.yml");
        try {
            YamlConfiguration overrides = new YamlConfiguration();
            overrides.loadFromString(extraYaml);
            for (String key : overrides.getKeys(true)) {
                if (!overrides.isConfigurationSection(key)) {
                    config.set(key, overrides.get(key));
                }
            }
        } catch (InvalidConfigurationException ex) {
            throw new IllegalArgumentException(ex);
        }
        return new LifeCoreSettings(new ConfigReader(config, "config.yml"));
    }
}
