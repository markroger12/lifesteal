package com.example.lifecore.event;

import org.bukkit.Location;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired when a revive beacon finishes its countdown. Cancelling it aborts the revival
 * (the beacon is removed without reviving the target).
 */
public class ReviveBeaconCompleteEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID beaconId;
    private final Location location;
    private final String tier;
    private final UUID ownerId;
    private final UUID targetId;
    private final String targetName;
    private boolean cancelled;

    public ReviveBeaconCompleteEvent(UUID beaconId, Location location, String tier, UUID ownerId, UUID targetId, String targetName) {
        this.beaconId = beaconId;
        this.location = location;
        this.tier = tier;
        this.ownerId = ownerId;
        this.targetId = targetId;
        this.targetName = targetName;
    }

    public UUID getBeaconId() {
        return beaconId;
    }

    public Location getLocation() {
        return location.clone();
    }

    public String getTier() {
        return tier;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public String getTargetName() {
        return targetName;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
