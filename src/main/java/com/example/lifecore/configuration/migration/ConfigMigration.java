package com.example.lifecore.configuration.migration;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * A structural migration step for one configuration file, e.g. renaming or moving keys.
 * Missing keys are added automatically afterwards, so steps only need to handle changes
 * that cannot be expressed as "a new key with a default value".
 */
public interface ConfigMigration {

    /** @return the file this migration applies to, e.g. {@code config.yml}. */
    String fileName();

    /** @return the version this step upgrades from (the result is fromVersion + 1). */
    int fromVersion();

    String description();

    void apply(YamlConfiguration config, MigrationReport report);
}
