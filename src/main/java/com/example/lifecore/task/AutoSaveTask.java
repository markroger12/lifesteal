package com.example.lifecore.task;

import com.example.lifecore.LifeCorePlugin;

/**
 * Periodically persists changed player data in one batch (async, on the database thread).
 */
public final class AutoSaveTask implements Runnable {

    private final LifeCorePlugin plugin;

    public AutoSaveTask(LifeCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        plugin.players().saveAllDirty().whenComplete((count, error) -> {
            if (error != null) {
                plugin.log().warn("[storage] Autosave failed (will retry): " + error.getMessage());
            } else if (count > 0) {
                plugin.log().debug("storage", () -> "Autosaved " + count + " player(s)");
            }
        });
    }
}
