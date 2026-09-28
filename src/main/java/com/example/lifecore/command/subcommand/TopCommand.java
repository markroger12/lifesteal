package com.example.lifecore.command.subcommand;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.api.LeaderboardEntry;
import com.example.lifecore.api.LeaderboardType;
import com.example.lifecore.command.CommandSupport;
import com.example.lifecore.command.SubCommand;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;

/**
 * /lifesteal top [hearts|kills|deaths|revives] - leaderboard in chat.
 */
public final class TopCommand extends SubCommand {

    public TopCommand(LifeCorePlugin plugin, CommandSupport support) {
        super(plugin, support);
    }

    @Override
    public String name() {
        return "top";
    }

    @Override
    public String permission() {
        return "lifecore.top";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!plugin.settings().leaderboards.enabled()) {
            plugin.messages().send(sender, "leaderboard.disabled");
            return;
        }
        LeaderboardType type = LeaderboardType.HEARTS;
        if (args.length > 0) {
            var parsed = LeaderboardType.fromId(args[0]);
            if (parsed.isEmpty()) {
                plugin.messages().send(sender, "leaderboard.unknown-type", Placeholders.of("type", args[0]));
                return;
            }
            type = parsed.get();
        }
        String typeName = plugin.messages().raw("leaderboard.types." + type.id());
        List<LeaderboardEntry> entries = plugin.leaderboards().get(type);
        plugin.messages().send(sender, "leaderboard.header", Placeholders.of("type", typeName));
        if (entries.isEmpty()) {
            plugin.messages().send(sender, "leaderboard.no-entries");
        }
        for (LeaderboardEntry entry : entries) {
            String value = type == LeaderboardType.HEARTS ? plugin.messages().hearts(entry.value()) : String.valueOf((long) entry.value());
            plugin.messages().send(sender, "leaderboard.entry", Placeholders.of("position", entry.position(), "name", entry.name(), "value", value)
                    .add("type", typeName));
        }
        plugin.messages().send(sender, "leaderboard.footer", Placeholders.of("type", typeName));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return CommandSupport.filter(Arrays.stream(LeaderboardType.values()).map(LeaderboardType::id).toList(), args[0]);
        }
        return List.of();
    }
}
