package com.example.lifecore.menu;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.api.LeaderboardType;
import com.example.lifecore.manager.heart.HeartMath;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;

/**
 * Executes configurable click actions:
 * <pre>
 * [close]                 close the menu
 * [open] &lt;menu&gt;           open player-menu, revive-menu, leaderboard-menu, check-menu, admin-menu
 * [player] &lt;command&gt;      run a command as the viewer
 * [console] &lt;command&gt;     run a command as the console ({player} = viewer)
 * [message] &lt;text&gt;        send a message
 * [sound] &lt;id&gt;            play a sound from sounds.yml
 * [withdraw] &lt;hearts&gt;     withdraw hearts into a note
 * [redeem]                redeem the note in the main hand
 * [refresh]               rebuild the current menu
 * [top] &lt;type&gt;            open the leaderboard for a type
 * </pre>
 */
public final class MenuActions {

    private final LifeCorePlugin plugin;

    public MenuActions(LifeCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void execute(Player viewer, List<String> actions, Placeholders placeholders, Menu source) {
        for (String raw : actions) {
            String action = raw.trim();
            if (!action.startsWith("[")) {
                continue;
            }
            int end = action.indexOf(']');
            if (end < 0) {
                continue;
            }
            String type = action.substring(1, end).toLowerCase(Locale.ROOT);
            String argument = placeholders.apply(action.substring(end + 1).trim());
            switch (type) {
                case "close" -> viewer.closeInventory();
                case "open" -> open(viewer, argument);
                case "player" -> {
                    String command = argument.startsWith("/") ? argument.substring(1) : argument;
                    if (!command.isBlank()) {
                        viewer.performCommand(command);
                    }
                }
                case "console" -> {
                    String command = argument.startsWith("/") ? argument.substring(1) : argument;
                    if (!command.isBlank()) {
                        plugin.scheduler().runGlobal(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
                    }
                }
                case "message" -> plugin.messages().sendRaw(viewer, argument, placeholders);
                case "sound" -> plugin.sounds().play(viewer, argument);
                case "withdraw" -> {
                    OptionalDouble amount = HeartMath.parse(argument, plugin.settings().hearts.hardLimit(), false,
                            plugin.settings().hearts.halfHearts());
                    if (amount.isPresent()) {
                        plugin.notes().withdraw(viewer, amount.getAsDouble());
                    } else {
                        plugin.log().warn("[menus] Invalid [withdraw] amount '" + argument + "'");
                    }
                }
                case "redeem" -> plugin.notes().redeem(viewer, EquipmentSlot.HAND);
                case "refresh" -> source.refresh();
                case "top" -> plugin.menus().openLeaderboard(viewer, LeaderboardType.fromId(argument).orElse(LeaderboardType.HEARTS), 0);
                default -> plugin.log().warn("[menus] Unknown menu action '" + raw + "'");
            }
        }
    }

    private void open(Player viewer, String menu) {
        switch (menu.toLowerCase(Locale.ROOT)) {
            case "player-menu", "main" -> plugin.menus().openPlayerMenu(viewer);
            case "revive-menu" -> plugin.menus().openReviveMenu(viewer, 0);
            case "leaderboard-menu" -> plugin.menus().openLeaderboard(viewer, LeaderboardType.HEARTS, 0);
            case "check-menu" -> plugin.menus().openCheck(viewer, viewer.getUniqueId());
            case "admin-menu" -> {
                if (viewer.hasPermission("lifecore.admin")) {
                    plugin.menus().openAdmin(viewer, 0);
                } else {
                    plugin.messages().send(viewer, "errors.no-permission");
                }
            }
            default -> plugin.log().warn("[menus] Unknown menu '" + menu + "' in an [open] action");
        }
    }
}
