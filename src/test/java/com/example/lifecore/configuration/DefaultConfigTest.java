package com.example.lifecore.configuration;

import com.example.lifecore.TestResources;
import com.example.lifecore.configuration.settings.LifeCoreSettings;
import com.example.lifecore.configuration.settings.StorageSettings;
import com.example.lifecore.configuration.settings.WorldRules;
import com.example.lifecore.configuration.settings.WorldRulesRegistry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the rule "every configuration key used in code must exist in the default configuration".
 */
class DefaultConfigTest {

    private static void assertAllAccessedPathsExist(YamlConfiguration defaults, ConfigReader reader) {
        List<String> missing = new ArrayList<>();
        for (String path : reader.accessedPaths()) {
            if (!defaults.contains(path, true)) {
                missing.add(path);
            }
        }
        assertTrue(missing.isEmpty(), "keys read by the code but missing from the defaults: " + missing);
        assertTrue(reader.problems().isEmpty(), "default configuration has problems: " + reader.problems());
    }

    @Test
    void mainConfigIsCompleteAndValid() {
        YamlConfiguration config = TestResources.yaml("config.yml");
        ConfigReader reader = new ConfigReader(config, "config.yml");
        LifeCoreSettings settings = new LifeCoreSettings(reader);
        assertAllAccessedPathsExist(TestResources.yaml("config.yml"), reader);
        assertEquals(10.0, settings.hearts.starting());
        assertEquals(2, settings.hearts.capTiers().size());
        assertTrue(settings.elimination.ban().enabled());
        assertFalse(settings.death.causes().isEmpty());
    }

    @Test
    void storageConfigIsCompleteAndValid() {
        YamlConfiguration config = TestResources.yaml("storage.yml");
        ConfigReader reader = new ConfigReader(config, "storage.yml");
        StorageSettings settings = StorageSettings.read(reader);
        assertAllAccessedPathsExist(TestResources.yaml("storage.yml"), reader);
        assertEquals(StorageSettings.Type.SQLITE, settings.type());
        assertEquals("lifecore_", settings.tablePrefix());
    }

    @Test
    void worldRulesResolvePerWorld() {
        YamlConfiguration config = TestResources.yaml("worlds.yml");
        config.set("safe-worlds", List.of("arena"));
        config.set("instant-elimination-worlds", List.of("hardcore"));
        ConfigReader reader = new ConfigReader(config, "worlds.yml");
        WorldRulesRegistry registry = new WorldRulesRegistry(reader);
        assertTrue(reader.problems().isEmpty(), reader.problems().toString());
        assertTrue(registry.get("world").lifestealActive());
        assertFalse(registry.get("lobby").enabled());
        assertFalse(registry.get("ARENA").lifestealActive(), "world names are case-insensitive");
        assertTrue(registry.get("hardcore").instantElimination());
        WorldRules unknown = registry.get("some_new_world");
        assertEquals(registry.defaults(), unknown);
    }

    @Test
    void invalidValuesAreReplacedAndReported() {
        LifeCoreSettings settings = TestResources.settings("""
                hearts:
                  starting: -5
                  maximum: .nan
                kill:
                  gain-mode: SOMETHING
                """);
        assertEquals(1.0, settings.hearts.starting(), "clamped to the minimum");
        assertEquals(LifeCoreSettings.GainMode.FIXED, settings.kill.gainMode());
    }

    @Test
    void everyBundledFileIsValidYamlWithAVersion() {
        for (String file : ConfigManager.FILES) {
            YamlConfiguration config = TestResources.yaml(file);
            assertTrue(config.getInt(ConfigMigrationManager.VERSION_KEY, 0) >= 1, file + " needs a config-version");
        }
    }
}
