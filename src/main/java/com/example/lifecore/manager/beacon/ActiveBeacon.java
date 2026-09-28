package com.example.lifecore.manager.beacon;

import com.example.lifecore.hook.hologram.Hologram;
import com.example.lifecore.model.BeaconRecord;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runtime state of an active revive beacon.
 * <p>
 * Countdown and durability are only mutated through synchronized methods, and the {@link #finish()}
 * latch guarantees a beacon completes, fails or is cancelled exactly once.
 */
public final class ActiveBeacon {

    private final UUID id;
    private final String tierId;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final UUID owner;
    private final String ownerName;
    private final UUID target;
    private final String targetName;
    private final long startedAt;
    private final UUID itemId;
    private final double maxDurability;
    private final AtomicBoolean finished = new AtomicBoolean();

    private int remainingSeconds;
    private double durability;
    private volatile boolean ownerNearby = true;
    private volatile Hologram hologram;
    private int secondsSinceSave;
    private int secondsSinceSound;

    public ActiveBeacon(UUID id, String tierId, String world, int x, int y, int z, UUID owner, String ownerName, UUID target,
                        String targetName, long startedAt, UUID itemId, int remainingSeconds, double durability, double maxDurability) {
        this.id = id;
        this.tierId = tierId;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.owner = owner;
        this.ownerName = ownerName;
        this.target = target;
        this.targetName = targetName;
        this.startedAt = startedAt;
        this.itemId = itemId;
        this.remainingSeconds = Math.max(0, remainingSeconds);
        this.maxDurability = Math.max(1, maxDurability);
        this.durability = Math.max(0, Math.min(this.maxDurability, durability));
    }

    public static ActiveBeacon fromRecord(BeaconRecord r) {
        return new ActiveBeacon(r.id(), r.tier(), r.world(), r.x(), r.y(), r.z(), r.owner(), r.ownerName(), r.target(),
                r.targetName(), r.startedAt(), r.itemId(), r.remainingSeconds(), r.durability(), r.maxDurability());
    }

    public synchronized BeaconRecord toRecord() {
        return new BeaconRecord(id, tierId, world, x, y, z, owner, ownerName, target, targetName, remainingSeconds,
                durability, maxDurability, startedAt, itemId);
    }

    public UUID id() {
        return id;
    }

    public String tierId() {
        return tierId;
    }

    public String worldName() {
        return world;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public UUID target() {
        return target;
    }

    public String targetName() {
        return targetName;
    }

    public long startedAt() {
        return startedAt;
    }

    public UUID itemId() {
        return itemId;
    }

    public double maxDurability() {
        return maxDurability;
    }

    public @Nullable Location location() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z);
    }

    public BlockKey blockKey() {
        return new BlockKey(world, x, y, z);
    }

    public synchronized int remainingSeconds() {
        return remainingSeconds;
    }

    /** Decrements the countdown by one second. @return the remaining seconds. */
    public synchronized int tickDown() {
        if (remainingSeconds > 0) {
            remainingSeconds--;
        }
        return remainingSeconds;
    }

    public synchronized double durability() {
        return durability;
    }

    /** Applies damage. @return durability left. */
    public synchronized double damage(double amount) {
        durability = Math.max(0, durability - Math.max(0, amount));
        return durability;
    }

    public synchronized int durabilityPercent() {
        return (int) Math.round(durability / maxDurability * 100.0);
    }

    public boolean isOwnerNearby() {
        return ownerNearby;
    }

    public void setOwnerNearby(boolean ownerNearby) {
        this.ownerNearby = ownerNearby;
    }

    public @Nullable Hologram hologram() {
        return hologram;
    }

    public void setHologram(@Nullable Hologram hologram) {
        this.hologram = hologram;
    }

    /** @return true exactly once - the caller owns the completion/failure/cancellation. */
    public boolean finish() {
        return finished.compareAndSet(false, true);
    }

    /** Re-arms a beacon whose completion could not be processed (e.g. database outage). */
    public void unfinish() {
        finished.set(false);
    }

    public boolean isFinished() {
        return finished.get();
    }

    public synchronized boolean shouldSave(int interval) {
        secondsSinceSave++;
        if (secondsSinceSave >= interval) {
            secondsSinceSave = 0;
            return true;
        }
        return false;
    }

    public synchronized boolean shouldPlayTickSound(int interval) {
        if (interval <= 0) {
            return false;
        }
        secondsSinceSound++;
        if (secondsSinceSound >= interval) {
            secondsSinceSound = 0;
            return true;
        }
        return false;
    }

    /** Immutable block position key. */
    public record BlockKey(String world, int x, int y, int z) {
        public static BlockKey of(Location location) {
            return new BlockKey(location.getWorld() == null ? "" : location.getWorld().getName(),
                    location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }
}
