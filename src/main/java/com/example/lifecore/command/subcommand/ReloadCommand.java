package com.example.lifecore.command.subcommand;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.command.CommandSupport;
import com.example.lifecore.command.SubCommand;
import org.bukkit.command.CommandSender;

/**
 * /lifesteal reload - reloads every configuration file except storage settings.
 */
public final class ReloadCommand extends SubCommand {

    public ReloadCommand(LifeCorePlugin plugin, CommandSupport support) {
        super(plugin, support);
    }

    @Override
    public String name() {
        return "reload";
    }

    @Override
    public String permission() {
        return "lifecore.admin.reload";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        plugin.reloadAll(sender);
    }
}
