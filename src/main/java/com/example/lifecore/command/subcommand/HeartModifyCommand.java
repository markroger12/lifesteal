package com.example.lifecore.command.subcommand;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.api.HeartChangeReason;
import com.example.lifecore.api.HeartChangeResult;
import com.example.lifecore.command.CommandSupport;
import com.example.lifecore.command.SubCommand;
import com.example.lifecore.manager.player.AdminActions;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.OptionalDouble;

/**
 * Shared implementation of setheart / addheart / removeheart.
 */
abstract class HeartModifyCommand extends SubCommand {

    private final AdminActions.Operation operation;

    HeartModifyCommand(LifeCorePlugin plugin, CommandSupport support, AdminActions.Operation operation) {
        super(plugin, support);
        this.operation = operation;
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            support.usage(sender, this, label);
            return;
        }
        OptionalDouble amount = support.amount(sender, args[1], operation == AdminActions.Operation.SET);
        if (amount.isEmpty()) {
            return;
        }
        support.resolve(sender, args[0], target -> plugin.admin()
                .modifyHearts(target.uuid(), operation, amount.getAsDouble(), sender, HeartChangeReason.ADMIN)
                .whenComplete((result, error) -> plugin.scheduler().runGlobal(() -> {
                    if (error != null) {
                        plugin.messages().send(sender, "errors.internal");
                        return;
                    }
                    if (result.isEmpty()) {
                        plugin.messages().send(sender, "errors.player-not-found", Placeholders.of("player", target.name()));
                        return;
                    }
                    HeartChangeResult change = result.get();
                    Placeholders ph = new Placeholders()
                            .add("player", target.name())
                            .add("amount", plugin.messages().hearts(amount.getAsDouble()))
                            .add("previous", plugin.messages().hearts(change.previous()))
                            .add("hearts", plugin.messages().hearts(change.current()));
                    String key;
                    if (change.eliminated()) {
                        key = "admin.hearts-eliminated";
                    } else if (change.cancelled()) {
                        key = "admin.hearts-unchanged";
                    } else if (change.capped()) {
                        key = "admin.hearts-capped";
                    } else {
                        key = "admin.hearts-updated";
                    }
                    plugin.messages().send(sender, key, ph);
                })));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return support.onlineNames(sender, args[0]);
        }
        if (args.length == 2) {
            return support.amounts(args[1]);
        }
        return List.of();
    }
}
