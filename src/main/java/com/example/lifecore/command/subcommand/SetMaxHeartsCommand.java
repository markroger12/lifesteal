package com.example.lifecore.command.subcommand;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.command.CommandSupport;
import com.example.lifecore.command.SubCommand;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/**
 * /lifesteal setmaxhearts &lt;player&gt; &lt;amount|reset&gt; - per-player heart cap override.
 */
public final class SetMaxHeartsCommand extends SubCommand {

    public SetMaxHeartsCommand(LifeCorePlugin plugin, CommandSupport support) {
        super(plugin, support);
    }

    @Override
    public String name() {
        return "setmaxhearts";
    }

    @Override
    public List<String> aliases() {
        return List.of("setmax", "maxhearts");
    }

    @Override
    public String permission() {
        return "lifecore.admin.setmax";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            support.usage(sender, this, label);
            return;
        }
        double value;
        if (args[1].equalsIgnoreCase("reset") || args[1].equalsIgnoreCase("default")) {
            value = -1;
        } else {
            OptionalDouble parsed = support.amount(sender, args[1], false);
            if (parsed.isEmpty()) {
                return;
            }
            value = parsed.getAsDouble();
        }
        support.resolve(sender, args[0], target -> plugin.admin().setMaxHearts(target.uuid(), value, sender)
                .thenAccept(max -> plugin.scheduler().runGlobal(() -> plugin.messages().send(sender,
                        max.isEmpty() ? "errors.player-not-found" : "admin.max-set",
                        Placeholders.of("player", target.name(), "max", plugin.messages().hearts(max.orElse(0.0)))))));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return support.onlineNames(sender, args[0]);
        }
        if (args.length == 2) {
            List<String> options = new ArrayList<>(List.of("reset", "20", "25", "30", "40"));
            return CommandSupport.filter(options, args[1]);
        }
        return List.of();
    }
}
