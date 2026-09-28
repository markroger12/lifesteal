package com.example.lifecore.command.subcommand;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.command.CommandSupport;
import com.example.lifecore.command.SubCommand;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * /lifesteal eliminate &lt;player&gt;
 */
public final class EliminateCommand extends SubCommand {

    public EliminateCommand(LifeCorePlugin plugin, CommandSupport support) {
        super(plugin, support);
    }

    @Override
    public String name() {
        return "eliminate";
    }

    @Override
    public String permission() {
        return "lifecore.admin.eliminate";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            support.usage(sender, this, label);
            return;
        }
        support.resolve(sender, args[0], target -> plugin.admin().eliminate(target.uuid(), sender)
                .whenComplete((result, error) -> plugin.scheduler().runGlobal(() -> {
                    Placeholders ph = Placeholders.of("player", target.name());
                    if (error != null) {
                        plugin.messages().send(sender, "errors.internal");
                    } else if (result.isEmpty()) {
                        plugin.messages().send(sender, "errors.player-not-found", ph);
                    } else {
                        plugin.messages().send(sender, result.get() ? "admin.eliminated" : "admin.eliminate-failed", ph);
                    }
                })));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1 ? support.onlineNames(sender, args[0]) : List.of();
    }
}
