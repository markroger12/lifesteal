package com.example.lifecore.menu;

import com.example.lifecore.LifeCorePlugin;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Captures the next chat message of a player (used by admin menus for search / amounts).
 */
public final class ChatInputManager {

    private record Prompt(Consumer<String> callback, long expiresAt) {
    }

    private final LifeCorePlugin plugin;
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();

    public ChatInputManager(LifeCorePlugin plugin) {
        this.plugin = plugin;
    }

    /** Asks the player for input. The callback runs on the player's thread. */
    public void request(Player player, String promptMessageKey, int timeoutSeconds, Consumer<String> callback) {
        long expires = System.currentTimeMillis() + timeoutSeconds * 1000L;
        Prompt prompt = new Prompt(callback, expires);
        prompts.put(player.getUniqueId(), prompt);
        player.closeInventory();
        plugin.messages().send(player, promptMessageKey);
        plugin.scheduler().runAtEntityLater(player, () -> {
            if (prompts.remove(player.getUniqueId(), prompt)) {
                plugin.messages().send(player, "chat-input.timeout");
            }
        }, timeoutSeconds * 20L);
    }

    /**
     * Handles a chat message. Called from the (async) chat event.
     *
     * @return true if the message was consumed as input
     */
    public boolean handle(Player player, String message) {
        Prompt prompt = prompts.remove(player.getUniqueId());
        if (prompt == null) {
            return false;
        }
        if (prompt.expiresAt() < System.currentTimeMillis()) {
            return false;
        }
        String input = message.trim();
        if (input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase(plugin.messages().raw("chat-input.cancel-word").toLowerCase(Locale.ROOT))) {
            plugin.messages().send(player, "chat-input.cancelled");
            return true;
        }
        plugin.scheduler().runAtEntity(player, () -> prompt.callback().accept(input));
        return true;
    }

    public void clear(UUID player) {
        prompts.remove(player);
    }
}
