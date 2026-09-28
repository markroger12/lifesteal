package com.example.lifecore.hook.placeholderapi;

import com.example.lifecore.LifeCorePlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * PlaceholderAPI expansion providing {@code %lifecore_*%} placeholders.
 */
public final class LifeCoreExpansion extends PlaceholderExpansion {

    private final LifeCorePlugin plugin;

    public LifeCoreExpansion(LifeCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return plugin.getName().toLowerCase(Locale.ROOT);
    }

    @Override
    public @NotNull String getAuthor() {
        List<String> authors = plugin.getDescription().getAuthors();
        return authors.isEmpty() ? "LifeCore" : String.join(", ", authors);
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @NotNull List<String> getPlaceholders() {
        String prefix = "%" + getIdentifier() + "_";
        return List.of(prefix + "hearts%", prefix + "max_hearts%", prefix + "min_hearts%", prefix + "kills%",
                prefix + "deaths%", prefix + "kdr%", prefix + "revives%", prefix + "status%", prefix + "eliminated%",
                prefix + "ban_time%", prefix + "revive_time%", prefix + "hearts_bar%", prefix + "top_hearts_1_name%",
                prefix + "top_hearts_1_value%");
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        return plugin.placeholders().resolve(player, params);
    }
}
