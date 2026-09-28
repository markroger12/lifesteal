package com.example.lifecore.util.scheduler;

import org.bukkit.plugin.Plugin;

/**
 * Picks the correct scheduler implementation for the running server.
 */
public final class SchedulerFactory {

    private SchedulerFactory() {
    }

    public static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    public static TaskScheduler create(Plugin plugin) {
        return isFolia() ? new FoliaTaskScheduler(plugin) : new BukkitTaskScheduler(plugin);
    }
}
