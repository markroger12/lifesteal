package com.example.lifecore.command.subcommand;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.command.CommandSupport;
import com.example.lifecore.command.SubCommand;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * /lifesteal reset &lt;player&gt; - restores starting hearts and clears elimination (and stats if configured).
 */
public final class ResetCommand extends SubCommand {

    public ResetCommand(LifeCorePlugin plugin, CommandSupport support) {
        super(plugin, support);
    }

    @Override
    public String name() {
        return "reset";
    }

    @Override
    public String permission() {
        return "lifecore.admin.reset";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            support.usage(sender, this, label);
            return;
        }
        support.resolve(sender, args[0], target -> plugin.admin().reset(target.uuid(), sender)
                .whenComplete((result, error) -> plugin.scheduler().runGlobal(() -> {
                    Placeholders ph = Placeholders.of("player", target.name(),
                            "hearts", plugin.messages().hearts(plugin.settings().hearts.starting()));
                    if (error != null) {
                        plugin.messages().send(sender, "errors.internal");
                    } else {
                        plugin.messages().send(sender, result.isPresent() ? "admin.reset" : "errors.player-not-found", ph);
                    }
                })));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1 ? support.onlineNames(sender, args[0]) : List.of();
    }
}
