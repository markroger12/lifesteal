package com.example.lifecore.configuration;

import com.example.lifecore.TestResources;
import com.example.lifecore.api.LeaderboardType;
import com.example.lifecore.api.PlayerStatus;
import com.example.lifecore.api.ReviveResult;
import com.example.lifecore.api.ReviveSource;
import com.example.lifecore.manager.antiexploit.AntiExploitManager;
import com.example.lifecore.manager.revive.ReviveManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every message key referenced in the source code must exist in messages.yml.
 */
class MessageKeysTest {

    private static final Pattern CALL = Pattern.compile(
            "(?:send|text|raw|lines|exists|broadcast|broadcastPermission|request)\\([^;]*?\"([a-z0-9-]+(?:\\.[a-z0-9_-]+)+)\"");
    /** Keys selected by ternaries, assignments or switch arms, e.g. {@code ? "kill.victim" : "kill.victim-no-loss"}. */
    private static final Pattern KEY_LITERAL = Pattern.compile("(?:\\?|:|->|=)\\s*\"([a-z-]+\\.[a-z0-9_.-]+)\"");

    @Test
    void allReferencedKeysExist() throws IOException {
        YamlConfiguration messages = TestResources.yaml("messages.yml");
        Set<String> referenced = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                collect(CALL.matcher(source), referenced);
                collect(KEY_LITERAL.matcher(source), referenced);
            }
        }
        referenced.removeIf(key -> key.endsWith("."));
        assertTrue(referenced.size() > 150, "the source scan found only " + referenced.size() + " keys - the pattern is broken");
        List<String> missing = new ArrayList<>();
        for (String key : referenced) {
            if (!messages.contains(key)) {
                missing.add(key);
            }
        }
        assertTrue(missing.isEmpty(), "message keys missing from messages.yml: " + missing);
    }

    private static void collect(Matcher matcher, Set<String> into) {
        while (matcher.find()) {
            into.add(matcher.group(1));
        }
    }

    @Test
    void dynamicKeysExist() {
        YamlConfiguration messages = TestResources.yaml("messages.yml");
        List<String> keys = new ArrayList<>();
        for (PlayerStatus status : PlayerStatus.values()) {
            keys.add("status." + status.name().toLowerCase(Locale.ROOT));
        }
        for (ReviveSource source : ReviveSource.values()) {
            keys.add("revive.sources." + source.name().toLowerCase(Locale.ROOT));
        }
        for (ReviveResult result : ReviveResult.values()) {
            keys.add(ReviveManager.resultKey(result));
        }
        for (LeaderboardType type : LeaderboardType.values()) {
            keys.add("leaderboard.types." + type.id());
        }
        for (String reason : List.of(AntiExploitManager.SAME_IP, AntiExploitManager.ALT_ACCOUNT,
                AntiExploitManager.REPEATED_KILL, AntiExploitManager.RAPID_KILLS)) {
            keys.add("anti-exploit.reasons." + reason);
        }
        for (String command : List.of("help", "menu", "check", "withdraw", "redeem", "revive", "top", "leaderboard",
                "resourcepack", "admin", "setheart", "addheart", "removeheart", "setmaxhearts", "eliminate", "reset",
                "give", "beacon", "reload", "info", "debug")) {
            keys.add("help.usage." + command);
            keys.add("help.descriptions." + command);
        }
        for (String unit : List.of("days", "hours", "minutes", "seconds", "permanent", "now")) {
            keys.add("time-units." + unit);
        }
        List<String> missing = keys.stream().filter(k -> !messages.contains(k)).toList();
        assertTrue(missing.isEmpty(), "dynamic message keys missing: " + missing);
    }
}
