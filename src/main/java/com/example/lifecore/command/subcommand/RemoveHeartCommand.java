package com.example.lifecore.command.subcommand;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.command.CommandSupport;
import com.example.lifecore.manager.player.AdminActions;

import java.util.List;

/**
 * /lifesteal removeheart &lt;player&gt; &lt;amount&gt;
 */
public final class RemoveHeartCommand extends HeartModifyCommand {

    public RemoveHeartCommand(LifeCorePlugin plugin, CommandSupport support) {
        super(plugin, support, AdminActions.Operation.REMOVE);
    }

    @Override
    public String name() {
        return "removeheart";
    }

    @Override
    public List<String> aliases() {
        return List.of("removehearts", "remove", "take");
    }

    @Override
    public String permission() {
        return "lifecore.admin.removehearts";
    }
}
