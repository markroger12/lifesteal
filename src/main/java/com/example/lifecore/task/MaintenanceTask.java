package com.example.lifecore.task;

import com.example.lifecore.LifeCorePlugin;

/**
 * Periodic async housekeeping: purges expired anti-exploit/cooldown/combat state and evicts
 * stale cache entries (logins that were denied after data was loaded).
 */
public final class MaintenanceTask implements Runnable {

    private final LifeCorePlugin plugin;

    public MaintenanceTask(LifeCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        try {
            plugin.antiExploit().purge();
            plugin.cooldowns().purge();
            plugin.combat().purge();
            // Eviction checks who is online, which belongs on a server thread.
            plugin.scheduler().runGlobal(() -> plugin.players().evictStale(120_000L));
        } catch (RuntimeException ex) {
            plugin.log().warn("[maintenance] Housekeeping failed: " + ex.getMessage());
        }
    }
}
