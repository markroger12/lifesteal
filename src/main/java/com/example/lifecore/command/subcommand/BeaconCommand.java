package com.example.lifecore.command.subcommand;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.command.CommandSupport;
import com.example.lifecore.command.SubCommand;
import com.example.lifecore.item.beacon.BeaconTier;
import com.example.lifecore.manager.beacon.ActiveBeacon;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * /lifesteal beacon list | cancel &lt;id&gt; [refund] | menu
 */
public final class BeaconCommand extends SubCommand {

    public BeaconCommand(LifeCorePlugin plugin, CommandSupport support) {
        super(plugin, support);
    }

    @Override
    public String name() {
        return "beacon";
    }

    @Override
    public List<String> aliases() {
        return List.of("beacons");
    }

    @Override
    public String permission() {
        return "lifecore.admin.beacons";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        String action = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> list(sender);
            case "menu" -> {
                if (sender instanceof Player player) {
                    plugin.menus().openAdminBeacons(player, 0);
                } else {
                    plugin.messages().send(sender, "errors.player-only");
                }
            }
            case "cancel" -> {
                if (args.length < 2) {
                    support.usage(sender, this, label);
                    return;
                }
                Optional<ActiveBeacon> beacon = find(args[1]);
                if (beacon.isEmpty()) {
                    plugin.messages().send(sender, "beacon.not-found", Placeholders.of("id", args[1]));
                    return;
                }
                boolean refund = args.length > 2 && args[2].equalsIgnoreCase("refund");
                plugin.scheduler().runGlobal(() -> {
                    if (plugin.beacons().cancel(beacon.get(), refund, "beacon.cancelled-by-admin")) {
                        plugin.messages().send(sender, "admin.beacon-cancelled", Placeholders.of("target", beacon.get().targetName()));
                    }
                });
            }
            default -> support.usage(sender, this, label);
        }
    }

    private void list(CommandSender sender) {
        var beacons = plugin.beacons().snapshot();
        plugin.messages().send(sender, "beacon.list-header", Placeholders.of("count", beacons.size()));
        for (ActiveBeacon beacon : beacons) {
            Optional<BeaconTier> tier = plugin.items().beacon(beacon.tierId());
            if (tier.isPresent()) {
                plugin.messages().send(sender, "beacon.list-entry", plugin.beacons().placeholders(beacon, tier.get()));
            }
        }
    }

    private Optional<ActiveBeacon> find(String idPrefix) {
        String prefix = idPrefix.toLowerCase(Locale.ROOT);
        return plugin.beacons().snapshot().stream().filter(b -> b.id().toString().startsWith(prefix)).findFirst();
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return CommandSupport.filter(List.of("list", "menu", "cancel"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("cancel")) {
            List<String> ids = new ArrayList<>();
            for (ActiveBeacon beacon : plugin.beacons().snapshot()) {
                ids.add(beacon.id().toString().substring(0, 8));
            }
            return CommandSupport.filter(ids, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("cancel")) {
            return CommandSupport.filter(List.of("refund"), args[2]);
        }
        return List.of();
    }
}
