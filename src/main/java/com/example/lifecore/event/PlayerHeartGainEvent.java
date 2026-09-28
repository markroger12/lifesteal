package com.example.lifecore.event;

import com.example.lifecore.api.HeartChangeReason;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired before a player gains hearts. Cancellable; the amount may be modified.
 */
public class PlayerHeartGainEvent extends PlayerHeartChangeEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public PlayerHeartGainEvent(UUID playerId, String playerName, double currentHearts, double amount, HeartChangeReason reason) {
        super(playerId, playerName, currentHearts, amount, reason);
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
