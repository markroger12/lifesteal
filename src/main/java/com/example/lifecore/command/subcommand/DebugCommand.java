package com.example.lifecore.command.subcommand;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.command.CommandSupport;
import com.example.lifecore.command.SubCommand;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.command.CommandSender;

/**
 * /lifesteal debug - toggles debug logging until the next reload/restart.
 */
public final class DebugCommand extends SubCommand {

    public DebugCommand(LifeCorePlugin plugin, CommandSupport support) {
        super(plugin, support);
    }

    @Override
    public String name() {
        return "debug";
    }

    @Override
    public String permission() {
        return "lifecore.admin.debug";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        boolean enabled = !plugin.log().isDebug();
        plugin.log().setDebug(enabled);
        plugin.messages().send(sender, "admin.debug", Placeholders.of("state",
                plugin.messages().raw(enabled ? "general.enabled" : "general.disabled")));
    }
}
