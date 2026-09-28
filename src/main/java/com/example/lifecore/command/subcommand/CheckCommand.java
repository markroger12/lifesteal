package com.example.lifecore.command.subcommand;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.command.CommandSupport;
import com.example.lifecore.command.SubCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /lifesteal check [player] [gui] - shows hearts, stats, status, ban and revival information.
 */
public final class CheckCommand extends SubCommand {

    public CheckCommand(LifeCorePlugin plugin, CommandSupport support) {
        super(plugin, support);
    }

    @Override
    public String name() {
        return "check";
    }

    @Override
    public List<String> aliases() {
        return List.of("lookup", "stats", "info-player");
    }

    @Override
    public String permission() {
        return "lifecore.check";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        boolean gui = args.length > 1 && args[1].equalsIgnoreCase("gui");
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                support.usage(sender, this, label);
                return;
            }
            show(sender, player.getName(), false);
            return;
        }
        if (!(sender instanceof Player self && self.getName().equalsIgnoreCase(args[0])) && !sender.hasPermission("lifecore.check.others")) {
            plugin.messages().send(sender, "errors.no-permission");
            return;
        }
        show(sender, args[0], gui);
    }

    private void show(CommandSender sender, String input, boolean gui) {
        support.resolve(sender, input, target -> {
            if (gui && sender instanceof Player viewer) {
                plugin.scheduler().runAtEntity(viewer, () -> plugin.menus().openCheck(viewer, target.uuid()));
                return;
            }
            plugin.players().loadOffline(target.uuid()).whenComplete((data, error) -> plugin.scheduler().runGlobal(() -> {
                if (error != null || data.isEmpty()) {
                    plugin.messages().send(sender, error != null ? "errors.database-unavailable" : "errors.player-not-found",
                            com.example.lifecore.util.text.Placeholders.of("player", input));
                    return;
                }
                plugin.messages().send(sender, "check.output", plugin.placeholders().playerPlaceholders(data.get(),
                        Bukkit.getPlayer(target.uuid())));
            }));
        });
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1 && sender.hasPermission("lifecore.check.others")) {
            return support.onlineNames(sender, args[0]);
        }
        if (args.length == 2) {
            return CommandSupport.filter(List.of("gui"), args[1]);
        }
        return List.of();
    }
}
