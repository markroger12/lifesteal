package com.example.lifecore.util.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.concurrent.CompletableFuture;

/**
 * Scheduler abstraction separating "which thread owns this work" from the platform.
 * <p>
 * On Paper/Spigot/Purpur everything that is not async runs on the main server thread.
 * On Folia, global work runs on the global region thread, entity work on the entity's
 * owning region and location work on the region owning the location.
 * <p>
 * All tick values are Minecraft ticks (1/20 s).
 */
public interface TaskScheduler {

    boolean isFolia();

    /** Runs global (non entity bound) work, immediately if already on the correct thread. */
    void runGlobal(Runnable task);

    ScheduledTask runGlobalLater(Runnable task, long delayTicks);

    ScheduledTask runGlobalTimer(Runnable task, long delayTicks, long periodTicks);

    /** Runs work that touches the given entity, immediately if already on its owning thread. */
    void runAtEntity(Entity entity, Runnable task);

    ScheduledTask runAtEntityLater(Entity entity, Runnable task, long delayTicks);

    /** Runs work that touches blocks/entities at the given location. */
    void runAtLocation(Location location, Runnable task);

    ScheduledTask runAtLocationLater(Location location, Runnable task, long delayTicks);

    void runAsync(Runnable task);

    ScheduledTask runAsyncTimer(Runnable task, long delayTicks, long periodTicks);

    /** Teleports an entity using the platform's safe teleport method. */
    CompletableFuture<Boolean> teleport(Entity entity, Location location);

    /** @return true if the current thread may modify the given entity. */
    boolean isOwnedByCurrentThread(Entity entity);

    /** @return true if the current thread may modify the given location. */
    boolean isOwnedByCurrentThread(Location location);

    /** @return true if the current thread is a server tick thread (main or any region). */
    boolean isTickThread();

    void cancelAll();
}
