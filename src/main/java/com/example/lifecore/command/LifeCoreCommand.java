package com.example.lifecore.command;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.command.subcommand.AddHeartCommand;
import com.example.lifecore.command.subcommand.AdminCommand;
import com.example.lifecore.command.subcommand.BeaconCommand;
import com.example.lifecore.command.subcommand.CheckCommand;
import com.example.lifecore.command.subcommand.DebugCommand;
import com.example.lifecore.command.subcommand.EliminateCommand;
import com.example.lifecore.command.subcommand.GiveCommand;
import com.example.lifecore.command.subcommand.HelpCommand;
import com.example.lifecore.command.subcommand.InfoCommand;
import com.example.lifecore.command.subcommand.LeaderboardCommand;
import com.example.lifecore.command.subcommand.MenuCommand;
import com.example.lifecore.command.subcommand.RedeemCommand;
import com.example.lifecore.command.subcommand.ReloadCommand;
import com.example.lifecore.command.subcommand.RemoveHeartCommand;
import com.example.lifecore.command.subcommand.ResetCommand;
import com.example.lifecore.command.subcommand.ResourcePackCommand;
import com.example.lifecore.command.subcommand.ReviveCommand;
import com.example.lifecore.command.subcommand.SetHeartCommand;
import com.example.lifecore.command.subcommand.SetMaxHeartsCommand;
import com.example.lifecore.command.subcommand.TopCommand;
import com.example.lifecore.command.subcommand.WithdrawCommand;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Root /lifesteal command (aliases /ls, /lifecore).
 */
public final class LifeCoreCommand implements TabExecutor {

    private final LifeCorePlugin plugin;
    private final Map<String, SubCommand> commands = new LinkedHashMap<>();
    private final Map<String, SubCommand> lookup = new LinkedHashMap<>();

    public LifeCoreCommand(LifeCorePlugin plugin) {
        this.plugin = plugin;
        CommandSupport support = new CommandSupport(plugin);
        register(new HelpCommand(plugin, support, this));
        register(new MenuCommand(plugin, support));
        register(new CheckCommand(plugin, support));
        register(new WithdrawCommand(plugin, support));
        register(new RedeemCommand(plugin, support));
        register(new ReviveCommand(plugin, support));
        register(new TopCommand(plugin, support));
        register(new LeaderboardCommand(plugin, support));
        register(new ResourcePackCommand(plugin, support));
        register(new AdminCommand(plugin, support));
        register(new SetHeartCommand(plugin, support));
        register(new AddHeartCommand(plugin, support));
        register(new RemoveHeartCommand(plugin, support));
        register(new SetMaxHeartsCommand(plugin, support));
        register(new EliminateCommand(plugin, support));
        register(new ResetCommand(plugin, support));
        register(new GiveCommand(plugin, support));
        register(new BeaconCommand(plugin, support));
        register(new ReloadCommand(plugin, support));
        register(new InfoCommand(plugin, support));
        register(new DebugCommand(plugin, support));
    }

    private void register(SubCommand command) {
        commands.put(command.name(), command);
        lookup.put(command.name(), command);
        for (String alias : command.aliases()) {
            lookup.put(alias, command);
        }
    }

    public Collection<SubCommand> commands() {
        return commands.values();
    }

    public SubCommand get(String name) {
        return lookup.get(name.toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            SubCommand defaultCommand = sender instanceof Player && sender.hasPermission("lifecore.menu") ? get("menu") : get("help");
            dispatch(sender, label, defaultCommand, new String[0]);
            return true;
        }
        SubCommand sub = get(args[0]);
        if (sub == null) {
            plugin.messages().send(sender, "errors.unknown-command", Placeholders.of("label", label));
            return true;
        }
        dispatch(sender, label, sub, Arrays.copyOfRange(args, 1, args.length));
        return true;
    }

    /** Runs a sub-command with permission, sender type and rate-limit checks. */
    public void dispatch(CommandSender sender, String label, SubCommand sub, String[] args) {
        if (!sub.permission().isEmpty() && !sender.hasPermission(sub.permission())) {
            plugin.messages().send(sender, "errors.no-permission");
            if (sender instanceof Player player) {
                plugin.sounds().play(player, "error");
            }
            return;
        }
        if (sub.playerOnly() && !(sender instanceof Player)) {
            plugin.messages().send(sender, "errors.player-only");
            return;
        }
        if (sender instanceof Player player && !player.hasPermission("lifecore.bypass.cooldown")
                && !plugin.antiExploit().allowCommand(player.getUniqueId())) {
            plugin.messages().send(sender, "errors.slow-down");
            return;
        }
        try {
            sub.execute(sender, label, args);
        } catch (RuntimeException ex) {
            plugin.log().error("Command /" + label + " " + sub.name() + " failed for " + sender.getName(), ex);
            plugin.messages().send(sender, "errors.internal");
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (SubCommand sub : commands.values()) {
                if (sub.isVisible(sender) && sub.name().startsWith(prefix)) {
                    out.add(sub.name());
                }
            }
            return out;
        }
        SubCommand sub = get(args[0]);
        if (sub == null || !sub.isVisible(sender)) {
            return List.of();
        }
        try {
            return sub.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
        } catch (RuntimeException ex) {
            return List.of();
        }
    }
}
