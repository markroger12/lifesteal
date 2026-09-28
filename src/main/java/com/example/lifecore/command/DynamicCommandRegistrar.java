package com.example.lifecore.command;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.util.compat.Compat;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Registers the configurable standalone aliases (commands.standalone-aliases) at startup.
 */
public final class DynamicCommandRegistrar {

    private final LifeCorePlugin plugin;
    private final List<AliasCommand> registered = new ArrayList<>();

    public DynamicCommandRegistrar(LifeCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void register(LifeCoreCommand root, Map<String, List<String>> aliases) {
        CommandMap map = Compat.commandMap();
        if (map == null) {
            plugin.log().warn("[commands] Could not access the command map - standalone aliases are unavailable.");
            return;
        }
        String fallbackPrefix = plugin.getName().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> entry : aliases.entrySet()) {
            if (root.get(entry.getKey()) == null) {
                plugin.log().warn("[commands] commands.standalone-aliases: unknown sub-command '" + entry.getKey() + "'");
                continue;
            }
            for (String name : entry.getValue()) {
                AliasCommand command = new AliasCommand(name, root, entry.getKey());
                if (map.register(fallbackPrefix, command)) {
                    registered.add(command);
                } else {
                    plugin.log().warn("[commands] /" + name + " is already used by another plugin - registered as /"
                            + fallbackPrefix + ":" + name + " only.");
                    registered.add(command);
                }
            }
        }
        if (!registered.isEmpty()) {
            plugin.log().info("[commands] Registered " + registered.size() + " standalone alias(es).");
        }
    }

    public void unregisterAll() {
        CommandMap map = Compat.commandMap();
        if (map != null) {
            for (AliasCommand command : registered) {
                command.unregister(map);
            }
        }
        registered.clear();
        if (plugin.isEnabled()) {
            Bukkit.getOnlinePlayers().forEach(player -> plugin.scheduler().runAtEntity(player, player::updateCommands));
        }
    }
}
