package com.example.lifecore.item.heart;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.api.HeartChangeReason;
import com.example.lifecore.api.HeartChangeResult;
import com.example.lifecore.api.LifeCoreItemType;
import com.example.lifecore.configuration.settings.LifeCoreSettings;
import com.example.lifecore.event.HeartItemConsumeEvent;
import com.example.lifecore.item.ItemIdentity;
import com.example.lifecore.item.ItemUseSupport;
import com.example.lifecore.manager.heart.HeartManager;
import com.example.lifecore.manager.heart.HeartMath;
import com.example.lifecore.model.PlayerData;
import com.example.lifecore.util.compat.Compat;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Optional;

/**
 * Consuming heart items (right-click).
 */
public final class HeartItemService {

    private final LifeCorePlugin plugin;

    public HeartItemService(LifeCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void use(Player player, EquipmentSlot hand, ItemStack item, ItemIdentity identity) {
        ItemUseSupport support = plugin.itemUse();
        if (!support.preChecks(player, LifeCoreSettings.CombatAction.HEART_ITEM)) {
            return;
        }
        Optional<HeartItemDefinition> found = plugin.items().heart(identity.id());
        if (found.isEmpty()) {
            plugin.messages().send(player, "items.unknown", Placeholders.of("id", identity.id()));
            return;
        }
        HeartItemDefinition definition = found.get();
        if (!definition.permission().isEmpty() && !player.hasPermission(definition.permission())) {
            plugin.messages().send(player, "errors.no-permission");
            plugin.sounds().play(player, "error");
            return;
        }
        PlayerData data = plugin.players().get(player);
        if (data == null) {
            plugin.messages().send(player, "errors.data-not-loaded");
            return;
        }
        if (data.isEliminated()) {
            plugin.messages().send(player, "items.eliminated");
            return;
        }
        long remaining = plugin.cooldowns().remaining("heart-item", player.getUniqueId(), definition.id());
        if (remaining > 0) {
            plugin.messages().send(player, "items.cooldown", Placeholders.of("time", plugin.messages().duration(remaining)));
            plugin.sounds().play(player, "error");
            return;
        }
        double max = plugin.hearts().getMaxHearts(player);
        boolean atMax = HeartMath.greaterOrEqual(data.getHearts(), max);
        Placeholders ph = new Placeholders()
                .add("hearts", plugin.messages().hearts(definition.hearts()))
                .add("max", plugin.messages().hearts(max))
                .add("item", definition.id());
        if (atMax && definition.atMaxHearts() == HeartItemDefinition.AtMaxBehavior.DENY) {
            plugin.messages().send(player, "heart-item.at-max", ph);
            plugin.sounds().play(player, "error");
            return;
        }
        HeartItemConsumeEvent event = new HeartItemConsumeEvent(player, LifeCoreItemType.HEART, definition.id(), item, definition.hearts());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        if (!support.consumeOne(player, hand, item)) {
            return;
        }
        plugin.cooldowns().set("heart-item", player.getUniqueId(), definition.id(), definition.cooldownMillis());
        if (atMax) {
            handleAtMax(player, definition, ph);
        } else {
            HeartChangeResult result = plugin.hearts().add(data, player, event.getHearts(), HeartChangeReason.HEART_ITEM,
                    HeartManager.ChangeOptions.standard().withFeedback(false));
            ph.add("gained", plugin.messages().hearts(Math.max(0, result.delta())))
                    .add("total", plugin.messages().hearts(data.getHearts()));
            plugin.messages().send(player, result.capped() ? "heart-item.used-capped" : "heart-item.used", ph);
        }
        support.runCommands(definition.commands(), player);
        plugin.sounds().play(player, definition.sound());
        plugin.particles().play(definition.particle(), player.getLocation());
        plugin.players().save(data);
    }

    private void handleAtMax(Player player, HeartItemDefinition definition, Placeholders ph) {
        switch (definition.atMaxHearts()) {
            case ABSORPTION -> {
                PotionEffectType absorption = Compat.potionEffect("absorption");
                if (absorption != null) {
                    int amplifier = Math.max(0, (int) Math.ceil(definition.hearts() / 2.0) - 1);
                    player.addPotionEffect(new PotionEffect(absorption, definition.absorptionSeconds() * 20, amplifier, false, true, true));
                }
                plugin.messages().send(player, "heart-item.at-max-absorption", ph);
            }
            case COMMANDS -> {
                plugin.itemUse().runCommands(definition.atMaxCommands(), player);
                plugin.messages().send(player, "heart-item.at-max-commands", ph);
            }
            default -> plugin.messages().send(player, "heart-item.at-max-consumed", ph);
        }
    }
}
