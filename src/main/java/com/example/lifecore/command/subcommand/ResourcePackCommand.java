package com.example.lifecore.command.subcommand;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.command.CommandSupport;
import com.example.lifecore.command.SubCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /lifesteal resourcepack - (re)sends the LifeCore resource pack.
 */
public final class ResourcePackCommand extends SubCommand {

    public ResourcePackCommand(LifeCorePlugin plugin, CommandSupport support) {
        super(plugin, support);
    }

    @Override
    public String name() {
        return "resourcepack";
    }

    @Override
    public List<String> aliases() {
        return List.of("rp", "pack");
    }

    @Override
    public String permission() {
        return "lifecore.resourcepack";
    }

    @Override
    public boolean playerOnly() {
        return true;
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!plugin.resourcePack().send((Player) sender)) {
            plugin.messages().send(sender, "resource-pack.not-configured");
        }
    }
}
