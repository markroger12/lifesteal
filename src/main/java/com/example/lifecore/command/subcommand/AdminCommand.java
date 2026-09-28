package com.example.lifecore.command.subcommand;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.command.CommandSupport;
import com.example.lifecore.command.SubCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /lifesteal admin [player] - opens the admin GUI (optionally directly for a player).
 */
public final class AdminCommand extends SubCommand {

    public AdminCommand(LifeCorePlugin plugin, CommandSupport support) {
        super(plugin, support);
    }

    @Override
    public String name() {
        return "admin";
    }

    @Override
    public String permission() {
        return "lifecore.admin.menu";
    }

    @Override
    public boolean playerOnly() {
        return true;
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        Player player = (Player) sender;
        if (args.length == 0) {
            plugin.menus().openAdmin(player, 0);
            return;
        }
        support.resolve(sender, args[0], target -> plugin.scheduler().runAtEntity(player,
                () -> plugin.menus().openAdminPlayer(player, target.uuid())));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1 ? support.onlineNames(sender, args[0]) : List.of();
    }
}
