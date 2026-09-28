package com.example.lifecore.manager;

import com.example.lifecore.api.PlayerStatus;
import com.example.lifecore.util.Guard;
import com.example.lifecore.util.LifeLogger;
import com.example.lifecore.util.TimeUtil;
import com.example.lifecore.util.scheduler.TaskScheduler;
import com.example.lifecore.util.text.NumberFormatter;
import com.example.lifecore.util.text.Placeholders;
import com.example.lifecore.util.text.TextUtil;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * Delivers every user-facing text from messages.yml.
 * <p>
 * A message may be a plain string (chat), a list (multi-line chat) or a section with any of
 * {@code chat}, {@code actionbar}, {@code title}, {@code subtitle}, {@code bossbar} and {@code sound}.
 * Text goes through: {key} placeholders -> PlaceholderAPI (optional) -> colours -> centring.
 */
public final class MessageService {

    private static final Set<String> MESSAGE_KEYS = Set.of("chat", "actionbar", "title", "subtitle", "bossbar", "sound",
            "fade-in", "stay", "fade-out");

    public record BossBarSpec(String text, BarColor color, BarStyle style, int seconds) {
    }

    public record Message(List<String> chat, String actionbar, String title, String subtitle, int fadeIn, int stay,
                          int fadeOut, BossBarSpec bossbar, String sound) {
        public boolean isEmpty() {
            return chat.isEmpty() && actionbar.isEmpty() && title.isEmpty() && subtitle.isEmpty() && bossbar == null;
        }
    }

    private final LifeLogger logger;
    private final TaskScheduler scheduler;
    private final SoundService sounds;
    private final Set<String> warnedMissing = ConcurrentHashMap.newKeySet();
    private volatile Map<String, Message> messages = Map.of();
    private volatile Map<String, Message> fallback = Map.of();
    private volatile String prefix = "";
    private volatile TimeUtil.TimeUnits timeUnits = TimeUtil.TimeUnits.DEFAULT;
    private volatile NumberFormatter numbers = new NumberFormatter(Locale.US);
    private volatile BiFunction<OfflinePlayer, String, String> externalPlaceholders = (player, text) -> text;

    public MessageService(LifeLogger logger, TaskScheduler scheduler, SoundService sounds) {
        this.logger = logger;
        this.scheduler = scheduler;
        this.sounds = sounds;
    }

    public void load(YamlConfiguration config, YamlConfiguration defaults, NumberFormatter numberFormatter) {
        Map<String, Message> parsed = new HashMap<>();
        parse(config, "", parsed);
        Map<String, Message> parsedDefaults = new HashMap<>();
        if (defaults != null) {
            parse(defaults, "", parsedDefaults);
        }
        this.prefix = config.getString("prefix", "");
        this.timeUnits = new TimeUtil.TimeUnits(
                config.getString("time-units.days", "d"),
                config.getString("time-units.hours", "h"),
                config.getString("time-units.minutes", "m"),
                config.getString("time-units.seconds", "s"),
                config.getString("time-units.permanent", "Permanent"),
                config.getString("time-units.now", "now"));
        this.numbers = numberFormatter;
        this.messages = Collections.unmodifiableMap(parsed);
        this.fallback = Collections.unmodifiableMap(parsedDefaults);
        this.warnedMissing.clear();
    }

    /** Installs the PlaceholderAPI bridge (or resets it with null). */
    public void setExternalPlaceholders(@Nullable BiFunction<OfflinePlayer, String, String> processor) {
        this.externalPlaceholders = processor == null ? (player, text) -> text : processor;
    }

    private void parse(ConfigurationSection section, String path, Map<String, Message> out) {
        for (String key : section.getKeys(false)) {
            String full = path.isEmpty() ? key : path + "." + key;
            if (section.isConfigurationSection(key)) {
                ConfigurationSection child = section.getConfigurationSection(key);
                if (child != null && isMessageSection(child)) {
                    out.put(full, parseSection(child));
                } else if (child != null) {
                    parse(child, full, out);
                }
            } else if (section.isList(key)) {
                out.put(full, new Message(List.copyOf(section.getStringList(key)), "", "", "", 10, 60, 10, null, ""));
            } else if (section.isString(key)) {
                String value = section.getString(key, "");
                out.put(full, new Message(value.isEmpty() ? List.of() : List.of(value.split("\n")), "", "", "", 10, 60, 10, null, ""));
            }
        }
    }

    private static boolean isMessageSection(ConfigurationSection section) {
        for (String key : section.getKeys(false)) {
            if (MESSAGE_KEYS.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private Message parseSection(ConfigurationSection section) {
        List<String> chat;
        if (section.isList("chat")) {
            chat = List.copyOf(section.getStringList("chat"));
        } else {
            String value = section.getString("chat", "");
            chat = value.isEmpty() ? List.of() : List.of(value.split("\n"));
        }
        BossBarSpec bossBar = null;
        ConfigurationSection bar = section.getConfigurationSection("bossbar");
        if (bar != null && !bar.getString("text", "").isEmpty()) {
            bossBar = new BossBarSpec(bar.getString("text", ""), parseEnum(BarColor.class, bar.getString("color", "RED"), BarColor.RED),
                    parseEnum(BarStyle.class, bar.getString("style", "SOLID"), BarStyle.SOLID), Math.max(1, bar.getInt("seconds", 5)));
        }
        return new Message(chat, section.getString("actionbar", ""), section.getString("title", ""),
                section.getString("subtitle", ""), Math.max(0, section.getInt("fade-in", 10)),
                Math.max(1, section.getInt("stay", 60)), Math.max(0, section.getInt("fade-out", 10)), bossBar,
                section.getString("sound", ""));
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E def) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return def;
        }
    }

    // ------------------------------------------------------------------ lookup

    public Message message(String key) {
        Message message = messages.get(key);
        if (message == null) {
            message = fallback.get(key);
            if (message == null) {
                if (warnedMissing.add(key)) {
                    logger.warn("[messages] Missing message '" + key + "' in messages.yml");
                }
                return new Message(List.of("&c<missing message: " + key + ">"), "", "", "", 10, 60, 10, null, "");
            }
        }
        return message;
    }

    public boolean exists(String key) {
        return messages.containsKey(key) || fallback.containsKey(key);
    }

    /** Applies placeholders, prefix and colours without a player context. */
    public String format(String raw, Placeholders placeholders) {
        return format(null, raw, placeholders);
    }

    public String format(@Nullable OfflinePlayer viewer, String raw, Placeholders placeholders) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String text = placeholders == null ? raw : placeholders.apply(raw);
        text = text.replace("{prefix}", prefix);
        if (viewer != null && text.indexOf('%') >= 0) {
            try {
                text = externalPlaceholders.apply(viewer, text);
            } catch (RuntimeException ex) {
                logger.debug("messages", () -> "PlaceholderAPI failed: " + ex.getMessage());
            }
        }
        return TextUtil.formatChatLine(text);
    }

    /** Formats a line for item names/lore/holograms (no centring). */
    public String formatItemText(@Nullable OfflinePlayer viewer, String raw, Placeholders placeholders) {
        return format(viewer, TextUtil.stripCenterTag(raw), placeholders);
    }

    /** @return the chat text of a message as one colourised string (lines joined with newlines). */
    public String text(String key, Placeholders placeholders) {
        return text(null, key, placeholders);
    }

    public String text(@Nullable OfflinePlayer viewer, String key, Placeholders placeholders) {
        List<String> lines = message(key).chat();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(format(viewer, lines.get(i), placeholders));
        }
        return builder.toString();
    }

    public List<String> lines(@Nullable OfflinePlayer viewer, String key, Placeholders placeholders) {
        List<String> out = new ArrayList<>();
        for (String line : message(key).chat()) {
            out.add(format(viewer, line, placeholders));
        }
        return out;
    }

    /** @return the raw, unformatted first chat line (used for labels such as status names). */
    public String raw(String key) {
        List<String> chat = message(key).chat();
        return chat.isEmpty() ? "" : chat.get(0);
    }

    // ------------------------------------------------------------------ delivery

    public void send(CommandSender to, String key) {
        send(to, key, Placeholders.EMPTY);
    }

    public void send(CommandSender to, String key, Placeholders placeholders) {
        if (to == null) {
            return;
        }
        deliver(to, message(key), placeholders);
    }

    /** Sends an ad-hoc text (e.g. a per-item custom message) through the same pipeline. */
    public void sendRaw(CommandSender to, String raw, Placeholders placeholders) {
        if (raw == null || raw.isEmpty()) {
            return;
        }
        OfflinePlayer viewer = to instanceof Player p ? p : null;
        for (String line : raw.split("\n")) {
            to.sendMessage(format(viewer, line, placeholders));
        }
    }

    public void broadcast(String key, Placeholders placeholders) {
        Message message = message(key);
        for (Player player : Bukkit.getOnlinePlayers()) {
            deliver(player, message, placeholders);
        }
        for (String line : message.chat()) {
            Bukkit.getConsoleSender().sendMessage(format(null, line, placeholders));
        }
    }

    public void broadcastPermission(String key, Placeholders placeholders, String permission) {
        Message message = message(key);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(permission)) {
                deliver(player, message, placeholders);
            }
        }
        for (String line : message.chat()) {
            Bukkit.getConsoleSender().sendMessage(format(null, line, placeholders));
        }
    }

    private void deliver(CommandSender to, Message message, Placeholders placeholders) {
        Player player = to instanceof Player p ? p : null;
        for (String line : message.chat()) {
            to.sendMessage(format(player, line, placeholders));
        }
        if (player == null) {
            return;
        }
        if (!message.actionbar().isEmpty()) {
            String text = format(player, TextUtil.stripCenterTag(message.actionbar()), placeholders);
            Guard.cosmetic(logger, "actionbar", () -> player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(text)));
        }
        if (!message.title().isEmpty() || !message.subtitle().isEmpty()) {
            String title = format(player, TextUtil.stripCenterTag(message.title()), placeholders);
            String subtitle = format(player, TextUtil.stripCenterTag(message.subtitle()), placeholders);
            Guard.cosmetic(logger, "title", () -> player.sendTitle(title, subtitle, message.fadeIn(), message.stay(), message.fadeOut()));
        }
        if (message.bossbar() != null) {
            Guard.cosmetic(logger, "bossbar", () -> showBossBar(player, message.bossbar(), placeholders));
        }
        if (!message.sound().isEmpty()) {
            sounds.play(player, message.sound());
        }
    }

    private void showBossBar(Player player, BossBarSpec spec, Placeholders placeholders) {
        BossBar bar = Bukkit.createBossBar(format(player, TextUtil.stripCenterTag(spec.text()), placeholders), spec.color(), spec.style());
        bar.setProgress(1.0);
        bar.addPlayer(player);
        int totalTicks = spec.seconds() * 20;
        int steps = Math.max(1, spec.seconds());
        for (int i = 1; i <= steps; i++) {
            double progress = Math.max(0.0, 1.0 - (double) i / steps);
            long delay = (long) i * totalTicks / steps;
            if (i == steps) {
                scheduler.runAtEntityLater(player, bar::removeAll, delay);
            } else {
                scheduler.runAtEntityLater(player, () -> bar.setProgress(progress), delay);
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    public String prefix() {
        return prefix;
    }

    public TimeUtil.TimeUnits timeUnits() {
        return timeUnits;
    }

    public String duration(long millis) {
        return TimeUtil.formatDuration(millis, timeUnits);
    }

    public NumberFormatter numbers() {
        return numbers;
    }

    public String hearts(double value) {
        return numbers.hearts(value);
    }

    public String status(PlayerStatus status) {
        return raw("status." + status.name().toLowerCase(Locale.ROOT));
    }
}
