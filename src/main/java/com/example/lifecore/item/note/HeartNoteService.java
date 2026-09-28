package com.example.lifecore.item.note;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.api.HeartChangeReason;
import com.example.lifecore.api.HeartChangeResult;
import com.example.lifecore.api.LifeCoreItemType;
import com.example.lifecore.configuration.settings.LifeCoreSettings;
import com.example.lifecore.event.HeartRedeemEvent;
import com.example.lifecore.event.HeartWithdrawEvent;
import com.example.lifecore.item.ItemIdentity;
import com.example.lifecore.item.ItemRegistry;
import com.example.lifecore.item.ItemUseSupport;
import com.example.lifecore.manager.heart.HeartManager;
import com.example.lifecore.manager.heart.HeartMath;
import com.example.lifecore.model.PlayerData;
import com.example.lifecore.storage.ConsumeResult;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Heart withdrawal into signed notes and redemption of those notes.
 * <p>
 * Every note carries a unique id and HMAC signature. Redemption atomically marks the id as
 * consumed in the database ledger before hearts are granted, so a duplicated note (from any
 * server-side dupe glitch or a creative copy) can only ever be redeemed once.
 */
public final class HeartNoteService {

    private final LifeCorePlugin plugin;
    private final Set<UUID> redeeming = ConcurrentHashMap.newKeySet();

    public HeartNoteService(LifeCorePlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ withdraw

    public void withdraw(Player player, double amount) {
        LifeCoreSettings s = plugin.settings();
        LifeCoreSettings.Withdraw config = s.withdraw;
        if (!config.enabled()) {
            plugin.messages().send(player, "withdraw.disabled");
            return;
        }
        ItemUseSupport support = plugin.itemUse();
        if (!support.preChecks(player, LifeCoreSettings.CombatAction.WITHDRAW)) {
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
        double minRemaining = Math.max(config.minRemaining(), s.hearts.minimum());
        Placeholders ph = new Placeholders()
                .add("amount", plugin.messages().hearts(amount))
                .add("min", plugin.messages().hearts(config.minAmount()))
                .add("max", plugin.messages().hearts(config.maxAmount()))
                .add("minimum", plugin.messages().hearts(minRemaining))
                .add("hearts", plugin.messages().hearts(data.getHearts()));
        if (!Double.isFinite(amount) || amount < config.minAmount() - 1e-9 || amount > config.maxAmount() + 1e-9
                || !HeartMath.isStep(amount, s.hearts.halfHearts())) {
            plugin.messages().send(player, "withdraw.invalid-amount", ph);
            plugin.sounds().play(player, "error");
            return;
        }
        if (data.getHearts() - amount < minRemaining - 1e-9) {
            plugin.messages().send(player, "withdraw.not-enough-hearts", ph);
            plugin.sounds().play(player, "error");
            return;
        }
        ItemRegistry.CreatedNote preview = plugin.items().createNote(amount, player.getUniqueId(), player.getName());
        if (config.fullInventory() == LifeCoreSettings.FullInventory.DENY && !support.hasSpace(player, preview.item())) {
            plugin.messages().send(player, "withdraw.inventory-full", ph);
            plugin.sounds().play(player, "error");
            return;
        }
        long remaining = plugin.cooldowns().remaining("withdraw", player.getUniqueId());
        if (remaining > 0) {
            plugin.messages().send(player, "withdraw.cooldown", ph.add("time", plugin.messages().duration(remaining)));
            return;
        }
        HeartWithdrawEvent event = new HeartWithdrawEvent(player, amount);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        HeartChangeResult result = plugin.hearts().remove(data, player, amount, HeartChangeReason.WITHDRAW,
                HeartManager.ChangeOptions.silent());
        double removed = Math.max(0, -result.delta());
        if (result.cancelled() || removed <= 0) {
            plugin.messages().send(player, "withdraw.failed", ph);
            return;
        }
        plugin.cooldowns().set("withdraw", player.getUniqueId(), config.cooldownMillis());
        ItemRegistry.CreatedNote note = Math.abs(removed - amount) < 1e-9 ? preview
                : plugin.items().createNote(removed, player.getUniqueId(), player.getName());
        support.giveOrDrop(player, note.item());
        registerNote(note, removed, player.getUniqueId());
        ph.add("amount", plugin.messages().hearts(removed)).add("hearts", plugin.messages().hearts(data.getHearts()));
        plugin.messages().send(player, "withdraw.success", ph);
        HeartNoteSettings settings = plugin.items().noteSettings();
        plugin.sounds().play(player, settings.soundWithdraw());
        plugin.particles().play(settings.particleWithdraw(), player.getLocation());
        plugin.players().save(data);
        plugin.log().info("withdraw", "Heart note issued", "player", player.getName(), "hearts", removed, "note", note.uniqueId());
    }

    private void registerNote(ItemRegistry.CreatedNote note, double hearts, UUID issuer) {
        plugin.database().execute("register note " + note.uniqueId(),
                s -> s.registerItem(note.uniqueId(), LifeCoreItemType.NOTE.id(), hearts, issuer, note.created()));
    }

    /** Creates a note for an administrator (/lifesteal give ... note <hearts>). */
    public ItemStack createAdminNote(double hearts, UUID issuer, String issuerName) {
        ItemRegistry.CreatedNote note = plugin.items().createNote(hearts, issuer, issuerName);
        registerNote(note, hearts, issuer);
        return note.item();
    }

    // ------------------------------------------------------------------ redeem

    public void redeem(Player player, EquipmentSlot hand) {
        LifeCoreSettings s = plugin.settings();
        if (!s.redeem.enabled()) {
            plugin.messages().send(player, "redeem.disabled");
            return;
        }
        ItemStack item = player.getInventory().getItem(hand);
        Optional<ItemIdentity> identity = plugin.items().identify(item);
        if (item == null || identity.isEmpty() || identity.get().type() != LifeCoreItemType.NOTE) {
            plugin.messages().send(player, "redeem.not-a-note");
            plugin.sounds().play(player, "error");
            return;
        }
        ItemIdentity note = identity.get();
        ItemUseSupport support = plugin.itemUse();
        if (!note.valid()) {
            support.handleInvalid(player, hand, item, "invalid heart note signature");
            return;
        }
        if (!support.preChecks(player, LifeCoreSettings.CombatAction.REDEEM)) {
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
        double value = note.value();
        if (!Double.isFinite(value) || value <= 0 || value > s.hearts.hardLimit()) {
            support.handleInvalid(player, hand, item, "heart note value out of range: " + value);
            return;
        }
        double max = plugin.hearts().getMaxHearts(player);
        double room = Math.max(0, max - data.getHearts());
        Placeholders ph = new Placeholders()
                .add("amount", plugin.messages().hearts(value))
                .add("max", plugin.messages().hearts(max))
                .add("hearts", plugin.messages().hearts(data.getHearts()));
        double redeemAmount = value;
        if (s.redeem.respectMaximum()) {
            if (room <= 1e-9) {
                plugin.messages().send(player, "redeem.at-max", ph);
                plugin.sounds().play(player, "error");
                return;
            }
            if (value > room + 1e-9) {
                if (!s.redeem.partialRedeem()) {
                    plugin.messages().send(player, "redeem.exceeds-max", ph);
                    plugin.sounds().play(player, "error");
                    return;
                }
                redeemAmount = HeartMath.floor(room, s.hearts.halfHearts());
                if (redeemAmount <= 0) {
                    plugin.messages().send(player, "redeem.at-max", ph);
                    return;
                }
            }
        }
        double remainder = HeartMath.floor(value - redeemAmount, s.hearts.halfHearts());
        UUID noteId = note.uniqueId();
        HeartRedeemEvent event = new HeartRedeemEvent(player, redeemAmount, noteId);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        if (!redeeming.add(noteId)) {
            return;
        }
        if (!support.consumeOne(player, hand, item)) {
            redeeming.remove(noteId);
            return;
        }
        ItemStack original = item.clone();
        original.setAmount(1);
        final double grant = redeemAmount;
        final double rest = remainder;
        plugin.database().submit("redeem note " + noteId,
                storage -> storage.consumeItem(noteId, LifeCoreItemType.NOTE.id(), value, player.getUniqueId(), System.currentTimeMillis()))
                .whenComplete((result, error) -> {
                    Player online = Bukkit.getPlayer(player.getUniqueId());
                    if (online == null) {
                        redeeming.remove(noteId);
                        finishOffline(player.getUniqueId(), noteId, result, error, grant + rest);
                        return;
                    }
                    plugin.scheduler().runAtEntity(online, () -> {
                        redeeming.remove(noteId);
                        finishRedeem(online, original, noteId, result, error, grant, rest, ph);
                    });
                });
    }

    private void finishRedeem(Player player, ItemStack original, UUID noteId, ConsumeResult result, Throwable error,
                              double grant, double rest, Placeholders ph) {
        ItemUseSupport support = plugin.itemUse();
        if (error != null) {
            support.giveOrDrop(player, original);
            plugin.messages().send(player, "errors.database-unavailable");
            plugin.sounds().play(player, "error");
            return;
        }
        if (result == ConsumeResult.ALREADY_CONSUMED) {
            plugin.log().warn("security", "Duplicate heart note redeem attempt", "player", player.getName(), "note", noteId);
            plugin.messages().broadcastPermission("security.duplicate-note-alert",
                    Placeholders.of("player", player.getName(), "note", noteId), "lifecore.notify");
            if (!plugin.settings().security.confiscateInvalid()) {
                support.giveOrDrop(player, original);
            }
            plugin.messages().send(player, "redeem.duplicate");
            plugin.sounds().play(player, "error");
            return;
        }
        PlayerData data = plugin.players().get(player);
        if (data == null) {
            finishOffline(player.getUniqueId(), noteId, result, null, grant + rest);
            return;
        }
        HeartChangeResult change = plugin.hearts().add(data, player, grant, HeartChangeReason.REDEEM,
                HeartManager.ChangeOptions.standard().withFeedback(false).withIgnoreCap(!plugin.settings().redeem.respectMaximum()));
        double granted = Math.max(0, change.delta());
        double leftover = HeartMath.floor(grant - granted + rest, plugin.settings().hearts.halfHearts());
        if (leftover > 0) {
            support.giveOrDrop(player, createAdminNote(leftover, player.getUniqueId(), player.getName()));
        }
        ph.add("amount", plugin.messages().hearts(granted))
                .add("remainder", plugin.messages().hearts(leftover))
                .add("hearts", plugin.messages().hearts(data.getHearts()));
        plugin.messages().send(player, leftover > 0 ? "redeem.partial" : "redeem.success", ph);
        HeartNoteSettings settings = plugin.items().noteSettings();
        plugin.sounds().play(player, settings.soundRedeem());
        plugin.particles().play(settings.particleRedeem(), player.getLocation());
        plugin.players().save(data);
        plugin.log().info("redeem", "Heart note redeemed", "player", player.getName(), "hearts", granted, "note", noteId);
    }

    /**
     * The player disconnected while the note was being validated. The note is already consumed,
     * so the hearts are credited to their stored data (bounded by the hard limit).
     */
    private void finishOffline(UUID player, UUID noteId, ConsumeResult result, Throwable error, double total) {
        if (error != null || result != ConsumeResult.CONSUMED) {
            plugin.log().warn("redeem", "Note redeem aborted because the player disconnected", "player", player, "note", noteId);
            return;
        }
        plugin.players().edit(player, data -> plugin.hearts().add(data, Bukkit.getPlayer(player), total, HeartChangeReason.REDEEM,
                HeartManager.ChangeOptions.silent().withIgnoreCap(true)));
        plugin.log().info("redeem", "Credited redeemed note to offline player", "player", player, "hearts", total, "note", noteId);
    }
}
