package com.example.lifecore.command.subcommand;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.command.CommandSupport;
import com.example.lifecore.command.SubCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /lifesteal menu - opens the player menu.
 */
public final class MenuCommand extends SubCommand {

    public MenuCommand(LifeCorePlugin plugin, CommandSupport support) {
        super(plugin, support);
    }

    @Override
    public String name() {
        return "menu";
    }

    @Override
    public List<String> aliases() {
        return List.of("gui");
    }

    @Override
    public String permission() {
        return "lifecore.menu";
    }

    @Override
    public boolean playerOnly() {
        return true;
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        plugin.menus().openPlayerMenu((Player) sender);
    }
}
