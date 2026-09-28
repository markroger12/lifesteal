package com.example.lifecore.listener;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.api.LifeCoreItemType;
import com.example.lifecore.item.ItemIdentity;
import com.example.lifecore.manager.beacon.ActiveBeacon;
import com.example.lifecore.util.Guard;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Right-click use of heart items, scrolls and notes; beacon placement; and protection against
 * LifeCore items being used as their vanilla material (eating, dyeing, placing).
 */
public final class ItemListener implements Listener {

    private final LifeCorePlugin plugin;

    public ItemListener(LifeCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        if (action == Action.RIGHT_CLICK_BLOCK && clicked != null && event.getHand() == EquipmentSlot.HAND) {
            Optional<ActiveBeacon> beacon = plugin.beacons().at(clicked);
            if (beacon.isPresent()) {
                event.setCancelled(true);
                plugin.items().beacon(beacon.get().tierId()).ifPresent(tier ->
                        plugin.messages().send(player, "beacon.status", plugin.beacons().placeholders(beacon.get(), tier)));
                return;
            }
        }
        EquipmentSlot hand = event.getHand();
        if (hand == null) {
            return;
        }
        ItemStack item = event.getItem();
        Optional<ItemIdentity> identity = plugin.items().identify(item);
        if (identity.isEmpty()) {
            return;
        }
        if (hand == EquipmentSlot.OFF_HAND && plugin.items().isLifeCoreItem(player.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
            return;
        }
        ItemIdentity id = identity.get();
        if (id.type() == LifeCoreItemType.BEACON) {
            // Placement is handled by BlockPlaceEvent; clicking air does nothing.
            return;
        }
        boolean denied = event.useItemInHand() == Event.Result.DENY;
        event.setCancelled(true);
        if (denied) {
            return;
        }
        Guard.run(plugin.log(), "item use by " + player.getName(), () -> {
            if (!id.valid() || id.type() == null) {
                plugin.itemUse().handleInvalid(player, hand, item, "invalid signature on " + (id.type() == null ? "unknown" : id.type().id()) + " item");
                return;
            }
            switch (id.type()) {
                case HEART -> plugin.heartItems().use(player, hand, item, id);
                case SCROLL -> plugin.scrolls().use(player, hand, item, id);
                case NOTE -> {
                    if (!player.hasPermission("lifecore.redeem")) {
                        plugin.messages().send(player, "errors.no-permission");
                        return;
                    }
                    plugin.notes().redeem(player, hand);
                }
                default -> {
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        Optional<ItemIdentity> identity = plugin.items().identify(item);
        if (identity.isEmpty()) {
            return;
        }
        if (identity.get().type() != LifeCoreItemType.BEACON) {
            event.setCancelled(true);
            return;
        }
        Guard.run(plugin.log(), "beacon placement by " + event.getPlayer().getName(),
                () -> plugin.beacons().handlePlace(event, identity.get()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
        if (plugin.items().isLifeCoreItem(item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (plugin.items().isLifeCoreItem(event.getItem())) {
            event.setCancelled(true);
            plugin.messages().send(event.getPlayer(), "items.cannot-eat", Placeholders.EMPTY);
        }
    }
}
