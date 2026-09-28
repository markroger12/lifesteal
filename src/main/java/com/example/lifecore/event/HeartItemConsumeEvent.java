package com.example.lifecore.event;

import com.example.lifecore.api.LifeCoreItemType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Fired before a player consumes a LifeCore heart item or sacrificial scroll.
 * For hearts, {@link #getHearts()} is the amount gained; for scrolls it is the heart cost.
 */
public class HeartItemConsumeEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final LifeCoreItemType itemType;
    private final String itemId;
    private final ItemStack item;
    private double hearts;
    private boolean cancelled;

    public HeartItemConsumeEvent(Player player, LifeCoreItemType itemType, String itemId, ItemStack item, double hearts) {
        super(player);
        this.itemType = itemType;
        this.itemId = itemId;
        this.item = item;
        this.hearts = hearts;
    }

    public LifeCoreItemType getItemType() {
        return itemType;
    }

    public String getItemId() {
        return itemId;
    }

    /** @return a copy of the consumed item. */
    public ItemStack getItem() {
        return item.clone();
    }

    public double getHearts() {
        return hearts;
    }

    public void setHearts(double hearts) {
        if (!Double.isFinite(hearts) || hearts < 0) {
            throw new IllegalArgumentException("Heart amount must be a finite, non-negative number");
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
