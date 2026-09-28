package com.example.lifecore.event;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Fired before a player is eliminated. If cancelled, the player keeps the configured minimum hearts instead.
 */
public class PlayerEliminationEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerId;
    private final String playerName;
    private final String cause;
    private final @Nullable String killerName;
    private long banDurationMillis;
    private boolean cancelled;

    public PlayerEliminationEvent(UUID playerId, String playerName, String cause, @Nullable String killerName, long banDurationMillis) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.cause = cause;
        this.killerName = killerName;
        this.banDurationMillis = banDurationMillis;
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

    /** @return the death cause key (e.g. PLAYER, LAVA, ADMIN). */
    public String getCause() {
        return cause;
    }

    public @Nullable String getKillerName() {
        return killerName;
    }

    /** @return ban length in milliseconds; 0 = no ban, -1 = permanent. */
    public long getBanDurationMillis() {
        return banDurationMillis;
    }

    public void setBanDurationMillis(long banDurationMillis) {
        if (banDurationMillis < -1) {
            throw new IllegalArgumentException("Ban duration must be >= -1");
        }
        this.banDurationMillis = banDurationMillis;
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
