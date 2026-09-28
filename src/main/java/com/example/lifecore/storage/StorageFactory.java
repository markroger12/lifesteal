package com.example.lifecore.storage;

import com.example.lifecore.configuration.settings.StorageSettings;
import com.example.lifecore.util.LifeLogger;

import java.io.File;

/**
 * Creates the configured storage backend.
 */
public final class StorageFactory {

    private StorageFactory() {
    }

    public static StorageProvider create(StorageSettings settings, File dataFolder, LifeLogger logger) {
        return switch (settings.type()) {
            case SQLITE -> {
                File file = new File(settings.sqliteFile());
                if (!file.isAbsolute()) {
                    file = new File(dataFolder, settings.sqliteFile());
                }
                yield new SQLiteStorage(file, settings.tablePrefix(), logger);
            }
            case MYSQL, MARIADB -> new MySQLStorage(settings, logger);
        };
    }
}
