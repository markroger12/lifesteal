package com.example.lifecore.util.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Folia scheduler implementation using the global region, region, entity and async schedulers.
 * <p>
 * This class references Paper/Folia-only API and is only ever loaded when Folia is detected,
 * so it never causes linkage errors on Spigot.
 */
public final class FoliaTaskScheduler implements TaskScheduler {

    private final Plugin plugin;

    public FoliaTaskScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isFolia() {
        return true;
    }

    @Override
    public void runGlobal(Runnable task) {
        if (Bukkit.isGlobalTickThread()) {
            task.run();
        } else {
            Bukkit.getGlobalRegionScheduler().execute(plugin, task);
        }
    }

    @Override
    public ScheduledTask runGlobalLater(Runnable task, long delayTicks) {
        return wrap(Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> task.run(), Math.max(1L, delayTicks)));
    }

    @Override
    public ScheduledTask runGlobalTimer(Runnable task, long delayTicks, long periodTicks) {
        return wrap(Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> task.run(), Math.max(1L, delayTicks), Math.max(1L, periodTicks)));
    }

    @Override
    public void runAtEntity(Entity entity, Runnable task) {
        if (Bukkit.isOwnedByCurrentRegion(entity)) {
            task.run();
        } else {
            entity.getScheduler().run(plugin, t -> task.run(), null);
        }
    }

    @Override
    public ScheduledTask runAtEntityLater(Entity entity, Runnable task, long delayTicks) {
        io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduled =
                entity.getScheduler().runDelayed(plugin, t -> task.run(), null, Math.max(1L, delayTicks));
        return scheduled == null ? ScheduledTask.NOOP : wrap(scheduled);
    }

    @Override
    public void runAtLocation(Location location, Runnable task) {
        if (Bukkit.isOwnedByCurrentRegion(location)) {
            task.run();
        } else {
            Bukkit.getRegionScheduler().execute(plugin, location, task);
        }
    }

    @Override
    public ScheduledTask runAtLocationLater(Location location, Runnable task, long delayTicks) {
        return wrap(Bukkit.getRegionScheduler().runDelayed(plugin, location, t -> task.run(), Math.max(1L, delayTicks)));
    }

    @Override
    public void runAsync(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run());
    }

    @Override
    public ScheduledTask runAsyncTimer(Runnable task, long delayTicks, long periodTicks) {
        return wrap(Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> task.run(),
                Math.max(1L, delayTicks) * 50L, Math.max(1L, periodTicks) * 50L, TimeUnit.MILLISECONDS));
    }

    @Override
    public CompletableFuture<Boolean> teleport(Entity entity, Location location) {
        return entity.teleportAsync(location);
    }

    @Override
    public boolean isOwnedByCurrentThread(Entity entity) {
        return Bukkit.isOwnedByCurrentRegion(entity);
    }

    @Override
    public boolean isOwnedByCurrentThread(Location location) {
        return Bukkit.isOwnedByCurrentRegion(location);
    }

    @Override
    public boolean isTickThread() {
        return Bukkit.isPrimaryThread();
    }

    @Override
    public void cancelAll() {
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
    }

    private static ScheduledTask wrap(io.papermc.paper.threadedregions.scheduler.ScheduledTask task) {
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
