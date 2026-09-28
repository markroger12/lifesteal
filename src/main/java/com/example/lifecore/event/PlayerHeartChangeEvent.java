package com.example.lifecore.event;

import com.example.lifecore.api.HeartChangeReason;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Base class for heart gain/loss events. The amount is always positive and may be changed
 * by listeners; the final value is still clamped by LifeCore's limits.
 */
public abstract class PlayerHeartChangeEvent extends Event implements Cancellable {

    private final UUID playerId;
    private final String playerName;
    private final double currentHearts;
    private final HeartChangeReason reason;
    private double amount;
    private boolean cancelled;

    protected PlayerHeartChangeEvent(UUID playerId, String playerName, double currentHearts, double amount, HeartChangeReason reason) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.currentHearts = currentHearts;
        this.amount = amount;
        this.reason = reason;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    /** @return the online player, or null if the change targets an offline player. */
    public @Nullable Player getPlayer() {
        return Bukkit.getPlayer(playerId);
    }

    public OfflinePlayer getOfflinePlayer() {
        return Bukkit.getOfflinePlayer(playerId);
    }

    public double getCurrentHearts() {
        return currentHearts;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        if (!Double.isFinite(amount) || amount < 0) {
            throw new IllegalArgumentException("Heart amount must be a finite, non-negative number");
        }
        this.amount = amount;
    }

    public HeartChangeReason getReason() {
        return reason;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
