package com.example.lifecore.event;

import com.example.lifecore.api.ReviveSource;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Fired before an eliminated player is revived. Cancellable; the restored hearts may be changed.
 */
public class PlayerReviveEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerId;
    private final String playerName;
    private final ReviveSource source;
    private final @Nullable UUID reviverId;
    private final String reviverName;
    private double hearts;
    private boolean cancelled;

    public PlayerReviveEvent(UUID playerId, String playerName, ReviveSource source, @Nullable UUID reviverId, String reviverName, double hearts) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.source = source;
        this.reviverId = reviverId;
        this.reviverName = reviverName;
        this.hearts = hearts;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public @Nullable Player getPlayer() {
        return Bukkit.getPlayer(playerId);
    }

    public ReviveSource getSource() {
        return source;
    }

    public @Nullable UUID getReviverId() {
        return reviverId;
    }

    public String getReviverName() {
        return reviverName;
    }

    public double getHearts() {
        return hearts;
    }

    public void setHearts(double hearts) {
        if (!Double.isFinite(hearts) || hearts <= 0) {
            throw new IllegalArgumentException("Revive hearts must be a finite, positive number");
        }
        this.hearts = hearts;
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
