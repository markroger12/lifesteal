package com.example.lifecore.configuration;

import com.example.lifecore.configuration.migration.ConfigMigration;
import com.example.lifecore.configuration.migration.MainConfigV1ToV2;
import com.example.lifecore.configuration.migration.MigrationReport;
import com.example.lifecore.util.LifeLogger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Detects outdated configuration files and upgrades them without destroying user settings.
 * <p>
 * For each file: a timestamped backup is written, structural {@link ConfigMigration} steps are
 * applied in order, missing keys (and their comments) are copied from the bundled defaults and
 * {@code config-version} is updated. Existing values are never overwritten. Keys inside
 * user-defined collections (e.g. item tiers) are not re-added, so deleting a default tier sticks.
 */
public final class ConfigMigrationManager {

    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    public static final String VERSION_KEY = "config-version";

    private final File dataFolder;
    private final Function<String, InputStream> resourceProvider;
    private final LifeLogger logger;
    private final Map<String, List<ConfigMigration>> migrations = new HashMap<>();
    private final Map<String, Set<String>> userCollections = new HashMap<>();

    public ConfigMigrationManager(File dataFolder, Function<String, InputStream> resourceProvider, LifeLogger logger) {
        this.dataFolder = dataFolder;
        this.resourceProvider = resourceProvider;
        this.logger = logger;
        register(new MainConfigV1ToV2());
        userCollections.put("config.yml", Set.of("hearts.permission-caps.tiers", "hearts.gain-multipliers",
                "hearts.loss-multipliers", "death.causes", "elimination.ban.durations", "commands.standalone-aliases"));
        userCollections.put("items.yml", Set.of("heart-items"));
        userCollections.put("effects.yml", Set.of("scrolls", "particles.effects"));
        userCollections.put("beacons.yml", Set.of("tiers"));
        userCollections.put("worlds.yml", Set.of("worlds"));
        userCollections.put("sounds.yml", Set.of());
        userCollections.put("menus.yml", Set.of("player-menu.items"));
        userCollections.put("storage.yml", Set.of("mysql.properties"));
        userCollections.put("messages.yml", Set.of());
    }

    public void register(ConfigMigration migration) {
        migrations.computeIfAbsent(migration.fileName(), k -> new ArrayList<>()).add(migration);
        migrations.get(migration.fileName()).sort(Comparator.comparingInt(ConfigMigration::fromVersion));
    }

    /** Result of processing a single file. */
    public record Outcome(YamlConfiguration config, YamlConfiguration defaults, State state, MigrationReport report, String error) {
    }

    public enum State {CREATED, UP_TO_DATE, MIGRATED, NEWER_THAN_PLUGIN, BROKEN}

    public Outcome process(String fileName) throws IOException {
        YamlConfiguration defaults = loadDefaults(fileName);
        File file = new File(dataFolder, fileName);
        if (!file.exists()) {
            copyDefault(fileName, file);
            try {
                return new Outcome(loadFile(file), defaults, State.CREATED, null, null);
            } catch (InvalidConfigurationException ex) {
                throw new IOException("Bundled resource " + fileName + " could not be parsed", ex);
            }
        }
        YamlConfiguration user;
        try {
            user = loadFile(file);
        } catch (InvalidConfigurationException ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            logger.error("[config] " + fileName + " contains invalid YAML and could not be loaded. "
                    + "The bundled defaults are used until you fix it (your file was NOT modified):\n" + message);
            return new Outcome(defaults, defaults, State.BROKEN, null, message);
        }
        int latest = defaults.getInt(VERSION_KEY, 1);
        int current = user.contains(VERSION_KEY) ? user.getInt(VERSION_KEY, 1) : 1;
        if (current > latest) {
            logger.warn("[config] " + fileName + " has config-version " + current + " but this LifeCore build only knows version "
                    + latest + ". Did you downgrade the plugin? The file is used as-is.");
            return new Outcome(user, defaults, State.NEWER_THAN_PLUGIN, null, null);
        }
        if (current == latest && user.contains(VERSION_KEY)) {
            return new Outcome(user, defaults, State.UP_TO_DATE, null, null);
        }
        MigrationReport report = new MigrationReport(fileName);
        report.versions(current, latest);
        report.backup(backup(file, current).getName());
        for (ConfigMigration migration : migrations.getOrDefault(fileName, List.of())) {
            if (migration.fromVersion() >= current && migration.fromVersion() < latest) {
                migration.apply(user, report);
            }
        }
        mergeMissing(user, defaults, userCollections.getOrDefault(fileName, Set.of()), report);
        user.set(VERSION_KEY, latest);
        user.save(file);
        logger.info("[config] Migrated " + report.summary());
        for (String change : report.changes()) {
            logger.info("[config]   - " + change);
        }
        return new Outcome(user, defaults, State.MIGRATED, report, null);
    }

    /**
     * Copies keys that exist in the defaults but not in the user configuration.
     */
    public static void mergeMissing(YamlConfiguration user, YamlConfiguration defaults, Set<String> collections, MigrationReport report) {
        for (String path : defaults.getKeys(true)) {
            if (path.equals(VERSION_KEY) || user.contains(path)) {
                continue;
            }
            if (isInsideExistingCollection(path, user, collections)) {
                continue;
            }
            if (defaults.isConfigurationSection(path)) {
                user.createSection(path);
            } else {
                user.set(path, defaults.get(path));
                report.keyAdded();
            }
            List<String> comments = defaults.getComments(path);
            if (!comments.isEmpty()) {
                user.setComments(path, comments);
            }
            List<String> inline = defaults.getInlineComments(path);
            if (!inline.isEmpty()) {
                user.setInlineComments(path, inline);
            }
        }
    }

    private static boolean isInsideExistingCollection(String path, YamlConfiguration user, Set<String> collections) {
        for (String collection : collections) {
            if (path.startsWith(collection + ".") && user.isConfigurationSection(collection)) {
                return true;
            }
        }
        return false;
    }

    public YamlConfiguration loadDefaults(String fileName) throws IOException {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.options().parseComments(true);
        try (InputStream in = resourceProvider.apply(fileName)) {
            if (in == null) {
                throw new IOException("Bundled resource " + fileName + " is missing from the plugin jar");
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                defaults.load(reader);
            }
        } catch (InvalidConfigurationException ex) {
            throw new IOException("Bundled resource " + fileName + " is invalid", ex);
        }
        return defaults;
    }

    private static YamlConfiguration loadFile(File file) throws IOException, InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.options().parseComments(true);
        config.load(file);
        return config;
    }

    private void copyDefault(String fileName, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create " + parent);
        }
        try (InputStream in = resourceProvider.apply(fileName)) {
            if (in == null) {
                throw new IOException("Bundled resource " + fileName + " is missing from the plugin jar");
            }
            Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private File backup(File file, int version) throws IOException {
        File backups = new File(dataFolder, "backups");
        if (!backups.exists() && !backups.mkdirs()) {
            throw new IOException("Unable to create backup folder " + backups);
        }
        String base = file.getName().replace(".yml", "");
        File backup = new File(backups, base + "-v" + version + "-" + LocalDateTime.now().format(BACKUP_STAMP) + ".yml");
        Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return backup;
    }
}
