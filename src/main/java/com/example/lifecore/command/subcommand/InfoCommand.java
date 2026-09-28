package com.example.lifecore.command.subcommand;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.command.CommandSupport;
import com.example.lifecore.command.SubCommand;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * /lifesteal info - version, platform, storage and integration status.
 */
public final class InfoCommand extends SubCommand {

    public InfoCommand(LifeCorePlugin plugin, CommandSupport support) {
        super(plugin, support);
    }

    @Override
    public String name() {
        return "info";
    }

    @Override
    public List<String> aliases() {
        return List.of("version", "about");
    }

    @Override
    public String permission() {
        return "lifecore.admin.info";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        plugin.messages().send(sender, "info.output", new Placeholders()
                .add("version", plugin.getDescription().getVersion())
                .add("server", Bukkit.getName() + " " + Bukkit.getBukkitVersion())
                .add("scheduler", plugin.scheduler().isFolia() ? "Folia (regionised)" : "Bukkit (main thread)")
                .add("storage", plugin.database().storage().describe())
                .add("storage_status", plugin.messages().raw(plugin.database().isHealthy() ? "info.healthy" : "info.unhealthy"))
                .add("cached", plugin.players().cached().size())
                .add("beacons", plugin.beacons().active().size())
                .add("hooks", String.join(", ", plugin.hooks().activeHooks()))
                .add("debug", plugin.log().isDebug()));
    }
}
