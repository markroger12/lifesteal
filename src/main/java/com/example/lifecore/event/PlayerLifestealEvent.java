package com.example.lifecore.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when a player kills another player and LifeCore is about to transfer hearts.
 * Cancelling it prevents any heart changes for both players (the death itself still happens).
 */
public class PlayerLifestealEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player killer;
    private final Player victim;
    private final @Nullable String blockedReason;
    private double killerGain;
    private double victimLoss;
    private boolean cancelled;

    public PlayerLifestealEvent(Player killer, Player victim, double killerGain, double victimLoss, @Nullable String blockedReason) {
        this.killer = killer;
        this.victim = victim;
        this.killerGain = killerGain;
        this.victimLoss = victimLoss;
        this.blockedReason = blockedReason;
    }

    public Player getKiller() {
        return killer;
    }

    public Player getVictim() {
        return victim;
    }

    public double getKillerGain() {
        return killerGain;
    }

    public void setKillerGain(double killerGain) {
        this.killerGain = requireValid(killerGain);
    }

    public double getVictimLoss() {
        return victimLoss;
    }

    public void setVictimLoss(double victimLoss) {
        this.victimLoss = requireValid(victimLoss);
    }

    /** @return the anti-exploit rule that blocked the reward, or null if the kill is legitimate. */
    public @Nullable String getBlockedReason() {
        return blockedReason;
    }

    public boolean isExploitBlocked() {
        return blockedReason != null;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    private static double requireValid(double value) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException("Heart amount must be a finite, non-negative number");
        }
        return value;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
