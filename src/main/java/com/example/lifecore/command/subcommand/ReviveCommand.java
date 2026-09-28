package com.example.lifecore.command.subcommand;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.api.ReviveSource;
import com.example.lifecore.command.CommandSupport;
import com.example.lifecore.command.SubCommand;
import com.example.lifecore.manager.revive.ReviveManager;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.OptionalDouble;

/**
 * /lifesteal revive &lt;player&gt; [hearts]
 * <p>
 * Admins (lifecore.admin.revive) revive for free and may choose the hearts restored.
 * Players (lifecore.revive) pay the configured cost; without a name the revive menu opens.
 */
public final class ReviveCommand extends SubCommand {

    public ReviveCommand(LifeCorePlugin plugin, CommandSupport support) {
        super(plugin, support);
    }

    @Override
    public String name() {
        return "revive";
    }

    @Override
    public String permission() {
        return "";
    }

    @Override
    public boolean isVisible(CommandSender sender) {
        return sender.hasPermission("lifecore.revive") || sender.hasPermission("lifecore.admin.revive");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        boolean admin = sender.hasPermission("lifecore.admin.revive");
        if (!admin && !sender.hasPermission("lifecore.revive")) {
            plugin.messages().send(sender, "errors.no-permission");
            return;
        }
        if (args.length == 0) {
            if (sender instanceof Player player) {
                plugin.menus().openReviveMenu(player, 0);
            } else {
                support.usage(sender, this, label);
            }
            return;
        }
        double hearts = -1;
        if (args.length > 1 && admin) {
            OptionalDouble parsed = support.amount(sender, args[1], false);
            if (parsed.isEmpty()) {
                return;
            }
            hearts = parsed.getAsDouble();
        }
        final double restored = hearts;
        support.resolve(sender, args[0], target -> {
            Placeholders ph = Placeholders.of("player", target.name(), "target", target.name());
            if (admin) {
                plugin.admin().revive(target.uuid(), sender, restored).thenAccept(result ->
                        plugin.scheduler().runGlobal(() -> plugin.messages().send(sender, ReviveManager.resultKey(result), ph)));
                return;
            }
            if (!(sender instanceof Player player)) {
                plugin.messages().send(sender, "errors.player-only");
                return;
            }
            plugin.scheduler().runAtEntity(player, () -> {
                if (plugin.settings().revive.playerRevive().confirm()) {
                    String problem = plugin.revive().checkPlayerReviveCost(player);
                    if (problem != null) {
                        plugin.messages().send(player, problem, plugin.revive().costPlaceholders(player).add("target", target.name()));
                        return;
                    }
                    Placeholders confirm = plugin.revive().costPlaceholders(player).add("target", target.name());
                    confirm.add("action", confirm.apply(plugin.messages().raw("menus.confirm-revive")));
                    plugin.menus().openConfirm(player, confirm,
                            () -> plugin.revive().playerRevive(player, target.uuid(), target.name(), ReviveSource.PLAYER));
                } else {
                    plugin.revive().playerRevive(player, target.uuid(), target.name(), ReviveSource.PLAYER);
                }
            });
        });
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> names = new java.util.ArrayList<>(CommandSupport.filter(plugin.revive().eliminatedNames(), args[0]));
            for (String online : support.onlineNames(sender, args[0])) {
                if (!names.contains(online)) {
                    names.add(online);
                }
            }
            return names;
        }
        if (args.length == 2 && sender.hasPermission("lifecore.admin.revive")) {
            return support.amounts(args[1]);
        }
        return List.of();
    }
}
