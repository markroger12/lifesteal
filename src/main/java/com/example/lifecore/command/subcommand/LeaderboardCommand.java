package com.example.lifecore.command.subcommand;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.api.LeaderboardType;
import com.example.lifecore.command.CommandSupport;
import com.example.lifecore.command.SubCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

/**
 * /lifesteal leaderboard [type] - leaderboard GUI (falls back to chat for the console).
 */
public final class LeaderboardCommand extends SubCommand {

    private final TopCommand chat;

    public LeaderboardCommand(LifeCorePlugin plugin, CommandSupport support) {
        super(plugin, support);
        this.chat = new TopCommand(plugin, support);
    }

    @Override
    public String name() {
        return "leaderboard";
    }

    @Override
    public List<String> aliases() {
        return List.of("lb", "leaderboards");
    }

    @Override
    public String permission() {
        return "lifecore.top";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            chat.execute(sender, label, args);
            return;
        }
        if (!plugin.settings().leaderboards.enabled()) {
            plugin.messages().send(sender, "leaderboard.disabled");
            return;
        }
        LeaderboardType type = args.length > 0 ? LeaderboardType.fromId(args[0]).orElse(LeaderboardType.HEARTS) : LeaderboardType.HEARTS;
        plugin.menus().openLeaderboard(player, type, 0);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return CommandSupport.filter(Arrays.stream(LeaderboardType.values()).map(LeaderboardType::id).toList(), args[0]);
        }
        return List.of();
    }
}
