package com.example.lifecore.command;

import com.example.lifecore.LifeCorePlugin;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * A /lifesteal sub-command.
 */
public abstract class SubCommand {

    protected final LifeCorePlugin plugin;
    protected final CommandSupport support;

    protected SubCommand(LifeCorePlugin plugin, CommandSupport support) {
        this.plugin = plugin;
        this.support = support;
    }

    public abstract String name();

    public List<String> aliases() {
        return List.of();
    }

    /** @return permission required, or empty for everyone. */
    public abstract String permission();

    public boolean playerOnly() {
        return false;
    }

    /** @return true if the sub-command should appear in help/tab for this sender. */
    public boolean isVisible(CommandSender sender) {
        return permission().isEmpty() || sender.hasPermission(permission());
    }

    /** Message key of the usage line. */
    public String usageKey() {
        return "help.usage." + name();
    }

    public abstract void execute(CommandSender sender, String label, String[] args);

    public List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
