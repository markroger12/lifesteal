package com.example.lifecore.util.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;

/**
 * Scheduler implementation for Paper, Spigot and Purpur (single main thread).
 */
public final class BukkitTaskScheduler implements TaskScheduler {

    private final Plugin plugin;

    public BukkitTaskScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isFolia() {
        return false;
    }

    @Override
    public void runGlobal(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    @Override
    public ScheduledTask runGlobalLater(Runnable task, long delayTicks) {
        if (!plugin.isEnabled()) {
            return ScheduledTask.NOOP;
        }
        return wrap(Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1L, delayTicks)));
    }

    @Override
    public ScheduledTask runGlobalTimer(Runnable task, long delayTicks, long periodTicks) {
        if (!plugin.isEnabled()) {
            return ScheduledTask.NOOP;
        }
        return wrap(Bukkit.getScheduler().runTaskTimer(plugin, task, Math.max(1L, delayTicks), Math.max(1L, periodTicks)));
    }

    @Override
    public void runAtEntity(Entity entity, Runnable task) {
        runGlobal(task);
    }

    @Override
    public ScheduledTask runAtEntityLater(Entity entity, Runnable task, long delayTicks) {
        return runGlobalLater(task, delayTicks);
    }

    @Override
    public void runAtLocation(Location location, Runnable task) {
        runGlobal(task);
    }

    @Override
    public ScheduledTask runAtLocationLater(Location location, Runnable task, long delayTicks) {
        return runGlobalLater(task, delayTicks);
    }

    @Override
    public void runAsync(Runnable task) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        } else {
            task.run();
        }
    }

    @Override
    public ScheduledTask runAsyncTimer(Runnable task, long delayTicks, long periodTicks) {
        if (!plugin.isEnabled()) {
            return ScheduledTask.NOOP;
        }
        return wrap(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, Math.max(1L, delayTicks), Math.max(1L, periodTicks)));
    }

    @Override
    public CompletableFuture<Boolean> teleport(Entity entity, Location location) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        runGlobal(() -> future.complete(entity.teleport(location)));
        return future;
    }

    @Override
    public boolean isOwnedByCurrentThread(Entity entity) {
        return Bukkit.isPrimaryThread();
    }

    @Override
    public boolean isOwnedByCurrentThread(Location location) {
        return Bukkit.isPrimaryThread();
    }

    @Override
    public boolean isTickThread() {
        return Bukkit.isPrimaryThread();
    }

    @Override
    public void cancelAll() {
        Bukkit.getScheduler().cancelTasks(plugin);
    }

    private static ScheduledTask wrap(BukkitTask task) {
        return new ScheduledTask() {
            @Override
            public void cancel() {
                task.cancel();
            }

            @Override
            public boolean isCancelled() {
                return task.isCancelled();
            }
        };
    }
}
