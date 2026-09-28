package com.example.lifecore.command.subcommand;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.command.CommandSupport;
import com.example.lifecore.manager.player.AdminActions;

import java.util.List;

/**
 * /lifesteal addheart &lt;player&gt; &lt;amount&gt;
 */
public final class AddHeartCommand extends HeartModifyCommand {

    public AddHeartCommand(LifeCorePlugin plugin, CommandSupport support) {
        super(plugin, support, AdminActions.Operation.ADD);
    }

    @Override
    public String name() {
        return "addheart";
    }

    @Override
    public List<String> aliases() {
        return List.of("addhearts", "add", "give-hearts");
    }

    @Override
    public String permission() {
        return "lifecore.admin.addhearts";
    }
}
