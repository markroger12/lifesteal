package com.example.lifecore.item.scroll;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.api.HeartChangeReason;
import com.example.lifecore.api.HeartChangeResult;
import com.example.lifecore.api.LifeCoreItemType;
import com.example.lifecore.configuration.settings.LifeCoreSettings;
import com.example.lifecore.event.HeartItemConsumeEvent;
import com.example.lifecore.item.ItemIdentity;
import com.example.lifecore.item.ItemRegistry;
import com.example.lifecore.item.ItemUseSupport;
import com.example.lifecore.manager.heart.HeartManager;
import com.example.lifecore.model.PlayerData;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.Optional;
import java.util.StringJoiner;

/**
 * Sacrificial scrolls: trade hearts for temporary potion effects.
 */
public final class ScrollService {

    private final LifeCorePlugin plugin;

    public ScrollService(LifeCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void use(Player player, EquipmentSlot hand, ItemStack item, ItemIdentity identity) {
        ItemUseSupport support = plugin.itemUse();
        if (!support.preChecks(player, LifeCoreSettings.CombatAction.SCROLL)) {
            return;
        }
        Optional<ScrollDefinition> found = plugin.items().scroll(identity.id());
        if (found.isEmpty()) {
            plugin.messages().send(player, "items.unknown", Placeholders.of("id", identity.id()));
            return;
        }
        ScrollDefinition scroll = found.get();
        if (!scroll.permission().isEmpty() && !player.hasPermission(scroll.permission())) {
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
        long remaining = plugin.cooldowns().remaining("scroll", player.getUniqueId(), scroll.id());
        if (remaining > 0) {
            plugin.messages().send(player, "items.cooldown", Placeholders.of("time", plugin.messages().duration(remaining)));
            plugin.sounds().play(player, "error");
            return;
        }
        Placeholders ph = new Placeholders()
                .add("cost", plugin.messages().hearts(scroll.heartCost()))
                .add("duration", scroll.durationSeconds())
                .add("effects", effectList(scroll))
                .add("minimum", plugin.messages().hearts(plugin.hearts().getMinHearts()));
        double after = data.getHearts() - scroll.heartCost();
        LifeCoreSettings s = plugin.settings();
        boolean wouldEliminate = s.elimination.enabled() && after <= s.elimination.threshold() + 1e-9;
        boolean belowMinimum = after < s.hearts.minimum() - 1e-9;
        if ((wouldEliminate || belowMinimum) && !scroll.allowElimination()) {
            plugin.messages().send(player, "scroll.not-enough-hearts", ph);
            plugin.sounds().play(player, "error");
            return;
        }
        HeartItemConsumeEvent event = new HeartItemConsumeEvent(player, LifeCoreItemType.SCROLL, scroll.id(), item, scroll.heartCost());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        if (!support.consumeOne(player, hand, item)) {
            return;
        }
        plugin.cooldowns().set("scroll", player.getUniqueId(), scroll.id(), scroll.cooldownMillis());
        for (ScrollDefinition.EffectSpec effect : scroll.effects()) {
            player.addPotionEffect(new PotionEffect(effect.type(), scroll.durationSeconds() * 20, effect.level() - 1, false, true, true));
        }
        plugin.sounds().play(player, scroll.sound());
        plugin.particles().play(scroll.particle(), player.getLocation());
        support.runCommands(scroll.commands(), player);
        HeartChangeResult result = event.getHearts() > 0
                ? plugin.hearts().remove(data, player, event.getHearts(), HeartChangeReason.SACRIFICE,
                new HeartManager.ChangeOptions(true, false, scroll.allowElimination(), false, "SACRIFICE", ""))
                : HeartChangeResult.cancelled(data.getHearts(), 0, HeartChangeReason.SACRIFICE);
        if (!result.eliminated()) {
            ph.add("hearts", plugin.messages().hearts(data.getHearts()));
            if (!scroll.useMessage().isEmpty()) {
                plugin.messages().sendRaw(player, scroll.useMessage(), ph);
            } else {
                plugin.messages().send(player, "scroll.used", ph);
            }
        }
        plugin.players().save(data);
    }

    private static String effectList(ScrollDefinition scroll) {
        StringJoiner joiner = new StringJoiner(", ");
        for (ScrollDefinition.EffectSpec effect : scroll.effects()) {
            joiner.add(ItemRegistry.prettyEffect(effect) + " " + ItemRegistry.roman(effect.level()));
        }
        return joiner.toString();
    }
}
