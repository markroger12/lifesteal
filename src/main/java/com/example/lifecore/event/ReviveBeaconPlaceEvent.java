package com.example.lifecore.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired when a player has placed a revive beacon and selected the player to revive,
 * right before the revival process starts.
 */
public class ReviveBeaconPlaceEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Location location;
    private final String tier;
    private final UUID targetId;
    private final String targetName;
    private boolean cancelled;

    public ReviveBeaconPlaceEvent(Player player, Location location, String tier, UUID targetId, String targetName) {
        super(player);
        this.location = location;
        this.tier = tier;
        this.targetId = targetId;
        this.targetName = targetName;
    }

    public Location getLocation() {
        return location.clone();
    }

    public String getTier() {
        return tier;
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
