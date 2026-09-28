package com.example.lifecore.command.subcommand;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.command.CommandSupport;
import com.example.lifecore.manager.player.AdminActions;

import java.util.List;

/**
 * /lifesteal setheart &lt;player&gt; &lt;amount&gt;
 */
public final class SetHeartCommand extends HeartModifyCommand {

    public SetHeartCommand(LifeCorePlugin plugin, CommandSupport support) {
        super(plugin, support, AdminActions.Operation.SET);
    }

    @Override
    public String name() {
        return "setheart";
    }

    @Override
    public List<String> aliases() {
        return List.of("sethearts", "set");
    }

    @Override
    public String permission() {
        return "lifecore.admin.sethearts";
    }
}
