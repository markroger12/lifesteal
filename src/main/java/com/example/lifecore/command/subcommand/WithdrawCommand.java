package com.example.lifecore.command.subcommand;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.command.CommandSupport;
import com.example.lifecore.command.SubCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.OptionalDouble;

/**
 * /lifesteal withdraw [amount] - converts hearts into a physical heart note.
 */
public final class WithdrawCommand extends SubCommand {

    public WithdrawCommand(LifeCorePlugin plugin, CommandSupport support) {
        super(plugin, support);
    }

    @Override
    public String name() {
        return "withdraw";
    }

    @Override
    public List<String> aliases() {
        return List.of("wd");
    }

    @Override
    public String permission() {
        return "lifecore.withdraw";
    }

    @Override
    public boolean playerOnly() {
        return true;
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        double amount = plugin.settings().withdraw.minAmount();
        if (args.length > 0) {
            OptionalDouble parsed = support.amount(sender, args[0], false);
            if (parsed.isEmpty()) {
                return;
            }
            amount = parsed.getAsDouble();
        }
        plugin.notes().withdraw((Player) sender, amount);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1 ? support.amounts(args[0]) : List.of();
    }
}
