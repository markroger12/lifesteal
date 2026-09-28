package com.example.lifecore.command;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.manager.heart.HeartMath;
import com.example.lifecore.manager.player.PlayerDataManager;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.function.Consumer;

/**
 * Shared helpers for commands: argument validation, async target resolution and completion.
 */
public final class CommandSupport {

    private static final List<String> AMOUNTS = List.of("1", "2", "3", "5", "10");
    private static final List<String> HALF_AMOUNTS = List.of("0.5", "1", "1.5", "2", "5", "10");

    private final LifeCorePlugin plugin;

    public CommandSupport(LifeCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void usage(CommandSender sender, SubCommand command, String label) {
        plugin.messages().send(sender, "errors.usage", Placeholders.of("usage",
                plugin.messages().raw(command.usageKey()).replace("{label}", label)));
    }

    /** Parses a heart amount; sends an error and returns empty when invalid. */
    public OptionalDouble amount(CommandSender sender, String input, boolean allowZero) {
        OptionalDouble value = HeartMath.parse(input, plugin.settings().hearts.hardLimit(), allowZero,
                plugin.settings().hearts.halfHearts());
        if (value.isEmpty()) {
            plugin.messages().send(sender, "errors.invalid-amount", Placeholders.of("input", input,
                    "max", plugin.messages().hearts(plugin.settings().hearts.hardLimit())));
        }
        return value;
    }

    /**
     * Resolves a player argument (online, offline known to LifeCore, or UUID) and runs the callback
     * on the global thread. Sends "player not found" automatically.
     */
    public void resolve(CommandSender sender, String input, Consumer<PlayerDataManager.Target> callback) {
        if (!PlayerDataManager.isValidName(input) && input.length() != 36) {
            plugin.messages().send(sender, "errors.invalid-player", Placeholders.of("player", input));
            return;
        }
        plugin.players().resolve(input).whenComplete((target, error) -> plugin.scheduler().runGlobal(() -> {
            if (error != null) {
                plugin.messages().send(sender, "errors.database-unavailable");
            } else if (target.isEmpty()) {
                plugin.messages().send(sender, "errors.player-not-found", Placeholders.of("player", input));
            } else {
                callback.accept(target.get());
            }
        }));
    }

    public List<String> onlineNames(CommandSender sender, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (sender instanceof Player viewer && !viewer.canSee(player)) {
                continue;
            }
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(lower)) {
                names.add(player.getName());
            }
        }
        return names;
    }

    public List<String> amounts(String prefix) {
        return filter(plugin.settings().hearts.halfHearts() ? HALF_AMOUNTS : AMOUNTS, prefix);
    }

    public static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
