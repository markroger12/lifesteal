package com.example.lifecore.configuration.migration;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * config.yml v1 -> v2.
 * <p>
 * Version 1 used a flat elimination ban layout ({@code elimination.ban: true} and
 * {@code elimination.ban-duration: 24h}) and named the IP rule {@code anti-exploit.same-ip-check}.
 * Version 2 groups ban settings under {@code elimination.ban} with permission based durations.
 */
public final class MainConfigV1ToV2 implements ConfigMigration {

    @Override
    public String fileName() {
        return "config.yml";
    }

    @Override
    public int fromVersion() {
        return 1;
    }

    @Override
    public String description() {
        return "Restructure elimination ban settings and rename anti-exploit.same-ip-check";
    }

    @Override
    public void apply(YamlConfiguration config, MigrationReport report) {
        Object banValue = config.get("elimination.ban");
        Object durationValue = config.get("elimination.ban-duration");
        if (banValue instanceof Boolean enabled) {
            config.set("elimination.ban", null);
            config.set("elimination.ban.enabled", enabled);
            report.change("elimination.ban (" + enabled + ") -> elimination.ban.enabled");
        }
        if (durationValue != null) {
            config.set("elimination.ban-duration", null);
            config.set("elimination.ban.durations.default", String.valueOf(durationValue));
            report.change("elimination.ban-duration (" + durationValue + ") -> elimination.ban.durations.default");
        }
        if (config.contains("anti-exploit.same-ip-check")) {
            Object value = config.get("anti-exploit.same-ip-check");
            config.set("anti-exploit.same-ip-check", null);
            if (!config.contains("anti-exploit.same-ip")) {
                config.set("anti-exploit.same-ip", value);
            }
            report.change("anti-exploit.same-ip-check -> anti-exploit.same-ip");
        }
        if (config.contains("hearts.max")) {
            Object value = config.get("hearts.max");
            config.set("hearts.max", null);
            if (!config.contains("hearts.maximum")) {
                config.set("hearts.maximum", value);
            }
            report.change("hearts.max -> hearts.maximum");
        }
        if (config.contains("hearts.min")) {
            Object value = config.get("hearts.min");
            config.set("hearts.min", null);
            if (!config.contains("hearts.minimum")) {
                config.set("hearts.minimum", value);
            }
            report.change("hearts.min -> hearts.minimum");
        }
    }
}
