package com.example.lifecore.placeholder;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.api.LeaderboardEntry;
import com.example.lifecore.api.LeaderboardType;
import com.example.lifecore.api.PlayerStatus;
import com.example.lifecore.model.PlayerData;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Resolves LifeCore placeholders for PlaceholderAPI ({@code %lifecore_<param>%}) and builds the
 * standard {@code {key}} placeholder set used by messages and menus.
 */
public final class PlaceholderResolver {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final LifeCorePlugin plugin;

    public PlaceholderResolver(LifeCorePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Resolves a PlaceholderAPI parameter (the part after {@code lifecore_}).
     *
     * @return the value, or null if the parameter is unknown
     */
    public @Nullable String resolve(@Nullable OfflinePlayer player, String params) {
        String param = params.toLowerCase(Locale.ROOT);
        if (param.startsWith("top_")) {
            return resolveTop(param);
        }
        if (player == null) {
            return "";
        }
        PlayerData data = plugin.players().get(player.getUniqueId());
        if (data == null) {
            return plugin.messages().raw("placeholders.not-loaded");
        }
        Player online = player.getPlayer();
        return switch (param) {
            case "hearts" -> plugin.messages().hearts(data.getHearts());
            case "hearts_int" -> String.valueOf((long) Math.floor(data.getHearts()));
            case "hearts_raw" -> String.valueOf(data.getHearts());
            case "health" -> online == null ? "0" : plugin.messages().hearts(online.getHealth() / 2.0);
            case "max_hearts" -> plugin.messages().hearts(plugin.hearts().getMaxHearts(data, online));
            case "min_hearts" -> plugin.messages().hearts(plugin.hearts().getMinHearts());
            case "kills" -> String.valueOf(data.getKills());
            case "deaths" -> String.valueOf(data.getDeaths());
            case "kdr" -> plugin.messages().numbers().ratio(kdr(data));
            case "revives" -> String.valueOf(data.getRevives());
            case "times_revived" -> String.valueOf(data.getTimesRevived());
            case "eliminations" -> String.valueOf(data.getEliminations());
            case "killstreak" -> String.valueOf(data.getKillStreak());
            case "best_killstreak" -> String.valueOf(data.getBestKillStreak());
            case "hearts_gained" -> plugin.messages().hearts(data.getHeartsGained());
            case "hearts_lost" -> plugin.messages().hearts(data.getHeartsLost());
            case "status" -> plugin.messages().status(data.getStatus());
            case "status_raw" -> data.getStatus().name();
            case "eliminated" -> bool(data.isEliminated());
            case "banned" -> bool(data.getStatus() == PlayerStatus.BANNED);
            case "ban_time" -> banTime(data);
            case "revive_time" -> reviveTime(data);
            case "hearts_bar" -> heartsBar(data.getHearts(), plugin.hearts().getMaxHearts(data, online));
            case "last_death_cause" -> data.getLastDeathCause().isEmpty() ? plugin.messages().raw("general.none")
                    : plugin.lifesteal().causeName(data.getLastDeathCause());
            case "last_killer" -> data.getLastKiller().isEmpty() ? plugin.messages().raw("general.none") : data.getLastKiller();
            case "in_combat" -> bool(online != null && plugin.combat().isInCombat(online));
            case "combat_time" -> String.valueOf(plugin.combat().remainingTag(player.getUniqueId()) / 1000L);
            default -> null;
        };
    }

    private @Nullable String resolveTop(String param) {
        // top_<type>_<position>_<name|value>
        String[] parts = param.split("_");
        if (parts.length != 4) {
            return null;
        }
        Optional<LeaderboardType> type = LeaderboardType.fromId(parts[1]);
        if (type.isEmpty()) {
            return null;
        }
        int position;
        try {
            position = Integer.parseInt(parts[2]);
        } catch (NumberFormatException ex) {
            return null;
        }
        List<LeaderboardEntry> entries = plugin.leaderboards().get(type.get());
        if (position < 1 || position > entries.size()) {
            return parts[3].equals("name") ? plugin.messages().raw("leaderboard.empty-name") : plugin.messages().raw("leaderboard.empty-value");
        }
        LeaderboardEntry entry = entries.get(position - 1);
        return switch (parts[3]) {
            case "name" -> entry.name();
            case "value" -> type.get() == LeaderboardType.HEARTS ? plugin.messages().hearts(entry.value())
                    : String.valueOf((long) entry.value());
            case "uuid" -> entry.uuid().toString();
            default -> null;
        };
    }

    private String bool(boolean value) {
        return plugin.messages().raw(value ? "general.yes" : "general.no");
    }

    private static double kdr(PlayerData data) {
        return data.getDeaths() == 0 ? data.getKills() : (double) data.getKills() / data.getDeaths();
    }

    public String banTime(PlayerData data) {
        long remaining = data.getRemainingBanMillis(System.currentTimeMillis());
        if (remaining == 0) {
            return plugin.messages().raw("general.none");
        }
        return remaining < 0 ? plugin.messages().timeUnits().permanent() : plugin.messages().duration(remaining);
    }

    public String reviveTime(PlayerData data) {
        long seconds = plugin.beacons().reviveTimeFor(data.getUniqueId());
        return seconds < 0 ? plugin.messages().raw("general.none") : plugin.messages().duration(seconds * 1000L);
    }

    /** Builds a heart bar such as ❤❤❤❤❤❥♡♡ using the symbols from messages.yml. */
    public String heartsBar(double hearts, double max) {
        String full = plugin.messages().raw("hearts-bar.full");
        String half = plugin.messages().raw("hearts-bar.half");
        String empty = plugin.messages().raw("hearts-bar.empty");
        int maxSymbols = 20;
        try {
            maxSymbols = Math.max(5, Math.min(60, Integer.parseInt(plugin.messages().raw("hearts-bar.max-symbols").trim())));
        } catch (NumberFormatException ignored) {
            // keep default
        }
        double cap = Math.max(1, Math.max(max, hearts));
        int symbols = (int) Math.min(maxSymbols, Math.ceil(cap));
        double perSymbol = cap / symbols;
        double filled = hearts / perSymbol;
        int fullCount = (int) Math.floor(filled + 1e-9);
        int halfCount = filled - fullCount >= 0.5 - 1e-9 && fullCount < symbols ? 1 : 0;
        int emptyCount = Math.max(0, symbols - fullCount - halfCount);
        return full.repeat(Math.min(fullCount, symbols)) + half.repeat(halfCount) + empty.repeat(emptyCount);
    }

    /** Standard placeholders describing a player (for messages, menus and lookups). */
    public Placeholders playerPlaceholders(PlayerData data, @Nullable Player online) {
        double max = plugin.hearts().getMaxHearts(data, online);
        return new Placeholders()
                .add("player", data.getName())
                .add("uuid", data.getUniqueId())
                .add("hearts", plugin.messages().hearts(data.getHearts()))
                .add("max_hearts", plugin.messages().hearts(max))
                .add("min_hearts", plugin.messages().hearts(plugin.hearts().getMinHearts()))
                .add("kills", data.getKills())
                .add("deaths", data.getDeaths())
                .add("kdr", plugin.messages().numbers().ratio(kdr(data)))
                .add("revives", data.getRevives())
                .add("times_revived", data.getTimesRevived())
                .add("eliminations", data.getEliminations())
                .add("killstreak", data.getKillStreak())
                .add("best_killstreak", data.getBestKillStreak())
                .add("hearts_gained", plugin.messages().hearts(data.getHeartsGained()))
                .add("hearts_lost", plugin.messages().hearts(data.getHeartsLost()))
                .add("status", plugin.messages().status(data.getStatus()))
                .add("eliminated", bool(data.isEliminated()))
                .add("ban_time", banTime(data))
                .add("revive_time", reviveTime(data))
                .add("hearts_bar", heartsBar(data.getHearts(), max))
                .add("last_death", data.getLastDeathAt() == 0 ? plugin.messages().raw("general.never")
                        : DATE.format(Instant.ofEpochMilli(data.getLastDeathAt())))
                .add("last_death_ago", data.getLastDeathAt() == 0 ? plugin.messages().raw("general.never")
                        : plugin.messages().duration(System.currentTimeMillis() - data.getLastDeathAt()))
                .add("last_death_cause", data.getLastDeathCause().isEmpty() ? plugin.messages().raw("general.none")
                        : plugin.lifesteal().causeName(data.getLastDeathCause()))
                .add("last_killer", data.getLastKiller().isEmpty() ? plugin.messages().raw("general.none") : data.getLastKiller())
                .add("eliminated_at", data.getEliminatedAt() == 0 ? plugin.messages().raw("general.never")
                        : DATE.format(Instant.ofEpochMilli(data.getEliminatedAt())))
                .add("elimination_cause", data.getEliminationCause().isEmpty() ? plugin.messages().raw("general.none")
                        : plugin.lifesteal().causeName(data.getEliminationCause()))
                .add("revived_by", data.getRevivedBy().isEmpty() ? plugin.messages().raw("general.none") : data.getRevivedBy())
                .add("revived_at", data.getRevivedAt() == 0 ? plugin.messages().raw("general.never")
                        : DATE.format(Instant.ofEpochMilli(data.getRevivedAt())))
                .add("first_join", data.getFirstJoin() == 0 ? plugin.messages().raw("general.never")
                        : DATE.format(Instant.ofEpochMilli(data.getFirstJoin())))
                .add("last_seen", online != null ? plugin.messages().raw("general.online")
                        : data.getLastSeen() == 0 ? plugin.messages().raw("general.never")
                        : DATE.format(Instant.ofEpochMilli(data.getLastSeen())))
                .add("online", bool(online != null || Bukkit.getPlayer(data.getUniqueId()) != null));
    }
}
