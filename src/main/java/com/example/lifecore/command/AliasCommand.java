package com.example.lifecore.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A standalone command (e.g. /withdraw) forwarding to a /lifesteal sub-command.
 */
public final class AliasCommand extends Command {

    private final LifeCoreCommand root;
    private final String subCommand;

    public AliasCommand(String name, LifeCoreCommand root, String subCommand) {
        super(name);
        this.root = root;
        this.subCommand = subCommand;
        setDescription("Alias for /lifesteal " + subCommand);
        setUsage("/" + name);
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        SubCommand sub = root.get(subCommand);
        if (sub != null) {
            root.dispatch(sender, label, sub, args);
        }
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        SubCommand sub = root.get(subCommand);
        if (sub == null || !sub.isVisible(sender)) {
            return List.of();
        }
        return sub.tabComplete(sender, args);
    }
}
