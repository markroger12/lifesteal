package com.example.lifecore.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired before a validated heart note is redeemed.
 */
public class HeartRedeemEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final double amount;
    private final UUID noteId;
    private boolean cancelled;

    public HeartRedeemEvent(Player player, double amount, UUID noteId) {
        super(player);
        this.amount = amount;
        this.noteId = noteId;
    }

    public double getAmount() {
        return amount;
    }

    public UUID getNoteId() {
        return noteId;
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
