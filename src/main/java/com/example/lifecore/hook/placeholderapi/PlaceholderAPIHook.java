package com.example.lifecore.hook.placeholderapi;

import com.example.lifecore.LifeCorePlugin;
import me.clip.placeholderapi.PlaceholderAPI;

/**
 * Registers the expansion and lets LifeCore messages use other plugins' placeholders.
 * Only loaded when PlaceholderAPI is installed.
 */
public final class PlaceholderAPIHook {

    private final LifeCorePlugin plugin;
    private LifeCoreExpansion expansion;

    public PlaceholderAPIHook(LifeCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        expansion = new LifeCoreExpansion(plugin);
        if (!expansion.register()) {
            plugin.log().warn("[hooks] PlaceholderAPI refused the LifeCore expansion.");
        }
        applyMessageBridge();
    }

    public void applyMessageBridge() {
        if (plugin.settings().integrations.parsePlaceholders()) {
            plugin.messages().setExternalPlaceholders(PlaceholderAPI::setPlaceholders);
        } else {
            plugin.messages().setExternalPlaceholders(null);
        }
    }

    public void unregister() {
        plugin.messages().setExternalPlaceholders(null);
        if (expansion != null && expansion.isRegistered()) {
            expansion.unregister();
        }
    }
}
