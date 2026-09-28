package com.example.lifecore.menu.admin;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.api.HeartChangeReason;
import com.example.lifecore.api.HeartChangeResult;
import com.example.lifecore.manager.heart.HeartMath;
import com.example.lifecore.manager.player.AdminActions;
import com.example.lifecore.manager.revive.ReviveManager;
import com.example.lifecore.menu.Menu;
import com.example.lifecore.menu.MenuLayout;
import com.example.lifecore.model.PlayerData;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Manage a single player (online or offline): hearts, max hearts, eliminate, revive, reset, stats.
 */
public final class AdminPlayerMenu extends Menu {

    private static final Set<String> SPECIAL = Set.of("profile", "add-heart", "add-half", "remove-heart", "remove-half",
            "set-hearts", "set-max", "eliminate", "revive", "reset", "stats", "back");

    private PlayerData target;

    public AdminPlayerMenu(LifeCorePlugin plugin, Player viewer, MenuLayout layout, PlayerData target) {
        super(plugin, viewer, layout);
        this.target = target;
    }

    @Override
    protected Placeholders placeholders() {
        return plugin.placeholders().playerPlaceholders(target, Bukkit.getPlayer(target.getUniqueId()))
                .add("admin", viewer.getName());
    }

    @Override
    protected void build() {
        Placeholders ph = placeholders();
        OfflinePlayer head = Bukkit.getOfflinePlayer(target.getUniqueId());
        placeAllStatic(ph, SPECIAL);
        placeStatic("profile", ph, head, null);
        placeStatic("add-heart", ph, null, click -> modify(AdminActions.Operation.ADD, 1));
        placeStatic("add-half", ph, null, click -> modify(AdminActions.Operation.ADD, 0.5));
        placeStatic("remove-heart", ph, null, click -> modify(AdminActions.Operation.REMOVE, 1));
        placeStatic("remove-half", ph, null, click -> modify(AdminActions.Operation.REMOVE, 0.5));
        placeStatic("set-hearts", ph, null, click -> askAmount("chat-input.enter-hearts",
                value -> modify(AdminActions.Operation.SET, value)));
        placeStatic("set-max", ph, null, click -> askAmount("chat-input.enter-max-hearts",
                value -> plugin.admin().setMaxHearts(target.getUniqueId(), value, viewer).thenAccept(max -> reopen(
                        "admin.max-set", Placeholders.of("max", plugin.messages().hearts(max.orElse(0.0)))))));
        placeStatic("eliminate", ph, null, click -> plugin.menus().openConfirm(viewer,
                ph.copy().add("action", ph.apply(plugin.messages().raw("menus.confirm-eliminate"))),
                () -> plugin.admin().eliminate(target.getUniqueId(), viewer).thenAccept(result -> reopen(
                        result.orElse(false) ? "admin.eliminated" : "admin.eliminate-failed", Placeholders.EMPTY))));
        placeStatic("revive", ph, null, click -> plugin.admin().revive(target.getUniqueId(), viewer, -1)
                .thenAccept(result -> reopen(ReviveManager.resultKey(result), Placeholders.EMPTY)));
        placeStatic("reset", ph, null, click -> plugin.menus().openConfirm(viewer,
                ph.copy().add("action", ph.apply(plugin.messages().raw("menus.confirm-reset"))),
                () -> plugin.admin().reset(target.getUniqueId(), viewer).thenAccept(result -> reopen("admin.reset", Placeholders.EMPTY))));
        placeStatic("stats", ph, null, click -> plugin.menus().openCheck(viewer, target.getUniqueId()));
        placeStatic("back", ph, null, click -> plugin.menus().openAdmin(viewer, 0));
    }

    private void askAmount(String promptKey, java.util.function.DoubleConsumer consumer) {
        plugin.chatInput().request(viewer, promptKey, 30, input -> {
            OptionalDouble value = HeartMath.parse(input, plugin.settings().hearts.hardLimit(), true, plugin.settings().hearts.halfHearts());
            if (value.isEmpty()) {
                plugin.messages().send(viewer, "errors.invalid-amount", Placeholders.of("input", input,
                        "max", plugin.messages().hearts(plugin.settings().hearts.hardLimit())));
                return;
            }
            consumer.accept(value.getAsDouble());
        });
    }

    private void modify(AdminActions.Operation operation, double amount) {
        CompletableFuture<Optional<HeartChangeResult>> future = plugin.admin().modifyHearts(target.getUniqueId(), operation,
                amount, viewer, HeartChangeReason.ADMIN);
        future.thenAccept(result -> reopen(result.isEmpty() ? "errors.player-not-found" : "admin.hearts-updated",
                Placeholders.of("hearts", plugin.messages().hearts(result.map(HeartChangeResult::current).orElse(0.0)))));
    }

    /** Reloads the target's data and re-renders the menu on the viewer's thread. */
    private void reopen(String messageKey, Placeholders extra) {
        plugin.players().loadOffline(target.getUniqueId()).thenAccept(data -> plugin.scheduler().runAtEntity(viewer, () -> {
            data.ifPresent(d -> target = d);
            Placeholders ph = placeholders().addAll(extra);
            plugin.messages().send(viewer, messageKey, ph);
            if (Menu.openMenu(viewer) == this) {
                refresh();
            } else {
                open();
            }
        }));
    }
}
