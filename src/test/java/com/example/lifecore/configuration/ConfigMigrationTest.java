package com.example.lifecore.configuration;

import com.example.lifecore.TestResources;
import com.example.lifecore.util.LifeLogger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMigrationTest {

    @TempDir
    Path folder;

    private ConfigMigrationManager manager() {
        return new ConfigMigrationManager(folder.toFile(), TestResources::open, new LifeLogger(Logger.getLogger("test")));
    }

    @Test
    void createsMissingFilesFromDefaults() throws Exception {
        ConfigMigrationManager.Outcome outcome = manager().process("config.yml");
        assertEquals(ConfigMigrationManager.State.CREATED, outcome.state());
        assertTrue(new File(folder.toFile(), "config.yml").exists());
        assertEquals(2, outcome.config().getInt("config-version"));
    }

    @Test
    void migratesVersionOneAndPreservesUserValues() throws Exception {
        String legacy = """
                config-version: 1
                hearts:
                  starting: 7.0
                  max: 30.0
                elimination:
                  ban: true
                  ban-duration: 12h
                anti-exploit:
                  same-ip-check: false
                """;
        Files.writeString(folder.resolve("config.yml"), legacy, StandardCharsets.UTF_8);
        ConfigMigrationManager.Outcome outcome = manager().process("config.yml");
        assertEquals(ConfigMigrationManager.State.MIGRATED, outcome.state());
        YamlConfiguration migrated = YamlConfiguration.loadConfiguration(folder.resolve("config.yml").toFile());
        assertEquals(2, migrated.getInt("config-version"));
        assertEquals(7.0, migrated.getDouble("hearts.starting"), "user values are preserved");
        assertEquals(30.0, migrated.getDouble("hearts.maximum"), "renamed keys keep their value");
        assertFalse(migrated.contains("hearts.max"));
        assertTrue(migrated.getBoolean("elimination.ban.enabled"));
        assertEquals("12h", migrated.getString("elimination.ban.durations.default"));
        assertFalse(migrated.getBoolean("anti-exploit.same-ip"));
        assertTrue(migrated.contains("kill.killer-gain"), "missing keys are added from the defaults");
        assertEquals(1.0, migrated.getDouble("kill.killer-gain"));
        File[] backups = folder.resolve("backups").toFile().listFiles();
        assertNotNull(backups);
        assertEquals(1, backups.length, "a backup is written before migrating");
        assertTrue(Files.readString(backups[0].toPath()).contains("ban-duration: 12h"));
        assertNotNull(outcome.report());
        assertTrue(outcome.report().addedKeys() > 10);
    }

    @Test
    void migratedConfigurationProducesValidSettings() throws Exception {
        Files.writeString(folder.resolve("config.yml"), "config-version: 1\nelimination:\n  ban-duration: 3h\n", StandardCharsets.UTF_8);
        ConfigMigrationManager.Outcome outcome = manager().process("config.yml");
        ConfigReader reader = new ConfigReader(outcome.config(), "config.yml");
        var settings = new com.example.lifecore.configuration.settings.LifeCoreSettings(reader);
        assertEquals(3L * 3_600_000L, settings.elimination.ban().defaultDurationMillis());
        assertTrue(reader.problems().isEmpty(), reader.problems().toString());
    }

    @Test
    void deletedUserCollectionsStayDeleted() throws Exception {
        String items = """
                config-version: 0
                heart-items:
                  small_heart:
                    hearts: 1.0
                """;
        Files.writeString(folder.resolve("items.yml"), items, StandardCharsets.UTF_8);
        manager().process("items.yml");
        YamlConfiguration migrated = YamlConfiguration.loadConfiguration(folder.resolve("items.yml").toFile());
        assertFalse(migrated.contains("heart-items.large_heart"), "a tier removed by the admin must not come back");
        assertTrue(migrated.contains("heart-note.item.material"), "other missing sections are restored");
    }

    @Test
    void upToDateFilesAreUntouched() throws Exception {
        manager().process("sounds.yml");
        File file = folder.resolve("sounds.yml").toFile();
        String before = Files.readString(file.toPath());
        ConfigMigrationManager.Outcome outcome = manager().process("sounds.yml");
        assertEquals(ConfigMigrationManager.State.UP_TO_DATE, outcome.state());
        assertEquals(before, Files.readString(file.toPath()));
    }

    @Test
    void brokenYamlFallsBackToDefaultsWithoutTouchingTheFile() throws Exception {
        String broken = "hearts:\n  starting: [unclosed\n";
        Files.writeString(folder.resolve("config.yml"), broken, StandardCharsets.UTF_8);
        ConfigMigrationManager.Outcome outcome = manager().process("config.yml");
        assertEquals(ConfigMigrationManager.State.BROKEN, outcome.state());
        assertEquals(broken, Files.readString(folder.resolve("config.yml")));
        assertEquals(10.0, outcome.config().getDouble("hearts.starting"));
    }

    @Test
    void newerFilesAreNotDowngraded() throws Exception {
        Files.writeString(folder.resolve("config.yml"), "config-version: 99\n", StandardCharsets.UTF_8);
        assertEquals(ConfigMigrationManager.State.NEWER_THAN_PLUGIN, manager().process("config.yml").state());
    }
}
