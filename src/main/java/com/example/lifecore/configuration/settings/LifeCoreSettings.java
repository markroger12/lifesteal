package com.example.lifecore.configuration.settings;

import com.example.lifecore.configuration.ConfigReader;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Immutable, validated view of config.yml. A new instance is created on every reload, so
 * readers always see a consistent set of values.
 */
public final class LifeCoreSettings {

    /** Minecraft caps max health at 1024 health points (512 hearts). */
    public static final double ABSOLUTE_MAX_HEARTS = 512.0;

    public final boolean debug;
    public final Locale numberLocale;
    public final Hearts hearts;
    public final Kill kill;
    public final Death death;
    public final Elimination elimination;
    public final Revive revive;
    public final Withdraw withdraw;
    public final Redeem redeem;
    public final HeartDrop heartDrop;
    public final AntiExploit antiExploit;
    public final Combat combat;
    public final Leaderboards leaderboards;
    public final Security security;
    public final boolean resetStatistics;
    public final ResourcePack resourcePack;
    public final Map<String, List<String>> standaloneAliases;
    public final Integrations integrations;

    public LifeCoreSettings(ConfigReader r) {
        this.debug = r.getBoolean("general.debug", false);
        this.numberLocale = parseLocale(r.getString("general.number-locale", "en_US"));
        this.hearts = readHearts(r.child("hearts"));
        this.kill = readKill(r.child("kill"));
        this.death = readDeath(r.child("death"));
        this.elimination = readElimination(r.child("elimination"));
        this.revive = readRevive(r.child("revive"), hearts);
        this.withdraw = readWithdraw(r.child("withdraw"), hearts);
        ConfigReader redeemReader = r.child("redeem");
        this.redeem = new Redeem(redeemReader.getBoolean("enabled", true), redeemReader.getBoolean("respect-maximum", true),
                redeemReader.getBoolean("partial-redeem", true));
        this.heartDrop = readHeartDrop(r.child("heart-drop"));
        this.antiExploit = readAntiExploit(r.child("anti-exploit"));
        this.combat = readCombat(r.child("combat"));
        ConfigReader lb = r.child("leaderboards");
        this.leaderboards = new Leaderboards(lb.getBoolean("enabled", true), lb.getInt("refresh-interval", 60, 5, 86400),
                lb.getInt("size", 10, 1, 100), lb.getBoolean("exclude-eliminated", true));
        ConfigReader sec = r.child("security");
        this.security = new Security(sec.getBoolean("verify-item-signatures", true), sec.getBoolean("confiscate-invalid-items", true),
                sec.getBoolean("log-invalid-items", true));
        this.resetStatistics = r.getBoolean("reset.reset-statistics", true);
        this.resourcePack = readResourcePack(r.child("resource-pack"));
        this.standaloneAliases = readAliases(r.child("commands.standalone-aliases"));
        this.integrations = readIntegrations(r.child("integrations"));
    }

    // ------------------------------------------------------------------ records

    public record PermissionValue(String id, String permission, double value) {
    }

    public record PermissionDuration(String id, String permission, long millis) {
    }

    public record Hearts(double starting, double minimum, double maximum, double hardLimit, boolean halfHearts,
                         boolean healOnGain, boolean enforceCapOnJoin, boolean permissionCapsEnabled,
                         boolean numericPermissions, List<PermissionValue> capTiers,
                         List<PermissionValue> gainMultipliers, List<PermissionValue> lossMultipliers) {
    }

    public enum GainMode {FIXED, STOLEN}

    public enum RewardMode {DIRECT, ITEM}

    public enum AtMaxMode {NOTHING, DROP_ITEM}

    public record Kill(boolean enabled, double killerGain, double victimLoss, GainMode gainMode, RewardMode rewardMode,
                       String rewardItem, AtMaxMode atMaxHearts, String overflowItem, boolean eliminatedKillersGain,
                       boolean indirectKills, long indirectWindowMillis, boolean broadcast, boolean ignoreNpcs) {
    }

    public record Death(boolean naturalDeathsLoseHearts, double defaultLoss, boolean byCause, Map<String, Double> causes,
                        Set<String> disabledCauses) {

        /** @return configured loss for a cause key, or empty if not configured. */
        public Double lossFor(String cause) {
            if (disabledCauses.contains(cause)) {
                return 0.0;
            }
            return causes.get(cause);
        }
    }

    public enum OnExpire {REVIVE, STAY_ELIMINATED}

    public record Ban(boolean enabled, boolean allowPermanent, long defaultDurationMillis, List<PermissionDuration> tiers,
                      OnExpire onExpire) {
    }

    public record Elimination(boolean enabled, double threshold, boolean spectator, boolean kick, boolean broadcast,
                              boolean lightning, boolean autoRespawn, boolean lockGamemode,
                              boolean preventSpectatorTeleport, boolean canChat, Set<String> allowedCommands, Ban ban) {
    }

    public enum TeleportMode {NONE, WORLD_SPAWN, BED}

    public record PlayerRevive(boolean enabled, double costHearts, double moneyCost, long cooldownMillis,
                               double minimumRemaining, boolean confirm) {
    }

    public record Revive(double hearts, GameMode gamemode, TeleportMode teleport, String spawnWorld, boolean heal,
                         boolean broadcast, PlayerRevive playerRevive) {
    }

    public enum FullInventory {DENY, DROP}

    public record Withdraw(boolean enabled, double minAmount, double maxAmount, double minRemaining,
                           FullInventory fullInventory, long cooldownMillis) {
    }

    public record Redeem(boolean enabled, boolean respectMaximum, boolean partialRedeem) {
    }

    public enum DropBehavior {GROUND, INVENTORY}

    public record HeartDrop(boolean enabled, String item, double chance, int amount, DropBehavior behavior,
                            String permission, Set<String> worlds, boolean requireLegitKill) {
    }

    public record AntiExploit(boolean sameIp, boolean repeatedKill, long repeatedKillDelayMillis, boolean preventAltFarming,
                              long altWindowMillis, boolean rapidKillsEnabled, int rapidMaxKills, long rapidWindowMillis,
                              boolean victimLosesWhenBlocked, boolean notifyStaff, long duplicateDeathWindowMillis,
                              long commandCooldownMillis, boolean blockCreativeItemUse) {
    }

    public enum CombatAction {WITHDRAW, REDEEM, REVIVE, MENU, HEART_ITEM, SCROLL, BEACON}

    public record Combat(long tagMillis, boolean punishCombatLogging, boolean useExternal, Set<CombatAction> blockedActions) {
    }

    public record Leaderboards(boolean enabled, int refreshSeconds, int size, boolean excludeEliminated) {
    }

    public record Security(boolean verifySignatures, boolean confiscateInvalid, boolean logInvalid) {
    }

    public record ResourcePack(boolean exportOnStartup, boolean sendOnJoin, String url, String sha1, boolean required,
                               boolean hostEnabled, int hostPort, String publicAddress) {
    }

    public record Integrations(boolean placeholderApi, boolean parsePlaceholders, boolean luckPerms, boolean vault,
                               boolean combatLogX, boolean deluxeCombat, boolean pvpManager, String holograms) {
    }

    // ------------------------------------------------------------------ readers

    private static Hearts readHearts(ConfigReader r) {
        double hardLimit = r.getDouble("hard-limit", 100.0, 1.0, ABSOLUTE_MAX_HEARTS);
        double minimum = r.getDouble("minimum", 1.0, 0.5, hardLimit);
        double maximum = r.getDouble("maximum", 20.0, 0.5, hardLimit);
        if (maximum < minimum) {
            r.problem("maximum", "maximum (" + maximum + ") is lower than minimum (" + minimum + "), using minimum as maximum");
            maximum = minimum;
        }
        double starting = r.getDouble("starting", 10.0, 0.5, hardLimit);
        if (starting < minimum || starting > maximum) {
            double clamped = Math.max(minimum, Math.min(maximum, starting));
            r.problem("starting", "starting hearts must be between minimum and maximum, using " + clamped);
            starting = clamped;
        }
        List<PermissionValue> caps = new ArrayList<>();
        ConfigReader tiers = r.child("permission-caps.tiers");
        for (String id : tiers.keys("")) {
            String permission = tiers.getString(id + ".permission", "");
            if (permission.isBlank()) {
                tiers.problem(id, "missing permission, tier ignored");
                continue;
            }
            caps.add(new PermissionValue(id, permission, tiers.getDouble(id + ".maximum", maximum, 0.5, hardLimit)));
        }
        return new Hearts(starting, minimum, maximum, hardLimit, r.getBoolean("half-hearts", true),
                r.getBoolean("heal-on-gain", true), r.getBoolean("enforce-cap-on-join", true),
                r.getBoolean("permission-caps.enabled", true), r.getBoolean("permission-caps.numeric-permissions", true),
                List.copyOf(caps), readMultipliers(r.child("gain-multipliers")), readMultipliers(r.child("loss-multipliers")));
    }

    private static List<PermissionValue> readMultipliers(ConfigReader r) {
        List<PermissionValue> list = new ArrayList<>();
        for (String id : r.keys("")) {
            String permission = r.getString(id + ".permission", "");
            if (permission.isBlank()) {
                r.problem(id, "missing permission, entry ignored");
                continue;
            }
            list.add(new PermissionValue(id, permission, r.getDouble(id + ".multiplier", 1.0, 0.0, 100.0)));
        }
        return List.copyOf(list);
    }

    private static Kill readKill(ConfigReader r) {
        return new Kill(
                r.getBoolean("enabled", true),
                r.getDouble("killer-gain", 1.0, 0.0, ABSOLUTE_MAX_HEARTS),
                r.getDouble("victim-loss", 1.0, 0.0, ABSOLUTE_MAX_HEARTS),
                r.getEnum("gain-mode", GainMode.class, GainMode.FIXED),
                r.getEnum("reward-mode", RewardMode.class, RewardMode.DIRECT),
                r.getString("reward-item", "small_heart"),
                r.getEnum("at-max-hearts", AtMaxMode.class, AtMaxMode.DROP_ITEM),
                r.getString("overflow-item", "small_heart"),
                r.getBoolean("eliminated-killers-gain", false),
                r.getBoolean("indirect-kills.enabled", true),
                r.getSecondsAsMillis("indirect-kills.window-seconds", 15),
                r.getBoolean("broadcast", false),
                r.getBoolean("ignore-npcs", true));
    }

    private static Death readDeath(ConfigReader r) {
        Map<String, Double> causes = new LinkedHashMap<>();
        for (String key : r.keys("causes")) {
            causes.put(key.toUpperCase(Locale.ROOT), r.getDouble("causes." + key, 1.0, 0.0, ABSOLUTE_MAX_HEARTS));
        }
        Set<String> disabled = new HashSet<>();
        for (String cause : r.getStringList("disabled-causes")) {
            disabled.add(cause.trim().toUpperCase(Locale.ROOT));
        }
        return new Death(r.getBoolean("natural-deaths-lose-hearts", true),
                r.getDouble("default-loss", 1.0, 0.0, ABSOLUTE_MAX_HEARTS),
                r.getBoolean("heart-loss-by-death-cause", true),
                Collections.unmodifiableMap(causes), Set.copyOf(disabled));
    }

    private static Elimination readElimination(ConfigReader r) {
        Set<String> allowed = new HashSet<>();
        for (String command : r.getStringList("allowed-commands")) {
            String normalized = command.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }
            if (!normalized.isEmpty()) {
                allowed.add(normalized);
            }
        }
        ConfigReader ban = r.child("ban");
        boolean allowPermanent = ban.getBoolean("allow-permanent", false);
        long defaultDuration;
        List<PermissionDuration> tiers = new ArrayList<>();
        ConfigReader durations = ban.child("durations");
        if (durations.has("default")) {
            defaultDuration = durations.getDuration("default", "24h", allowPermanent);
        } else {
            defaultDuration = 24L * 60 * 60 * 1000;
        }
        for (String id : durations.keys("")) {
            if (id.equalsIgnoreCase("default")) {
                continue;
            }
            String permission = durations.getString(id + ".permission", "");
            if (permission.isBlank()) {
                durations.problem(id, "missing permission, entry ignored");
                continue;
            }
            tiers.add(new PermissionDuration(id, permission, durations.getDuration(id + ".duration", "24h", allowPermanent)));
        }
        Ban banSettings = new Ban(ban.getBoolean("enabled", true), allowPermanent, defaultDuration, List.copyOf(tiers),
                ban.getEnum("on-expire", OnExpire.class, OnExpire.REVIVE));
        return new Elimination(
                r.getBoolean("enabled", true),
                r.getDouble("threshold", 0.0, 0.0, ABSOLUTE_MAX_HEARTS),
                r.getBoolean("spectator", true),
                r.getBoolean("kick", true),
                r.getBoolean("broadcast", true),
                r.getBoolean("lightning-effect", true),
                r.getBoolean("auto-respawn", true),
                r.getBoolean("lock-gamemode", true),
                r.getBoolean("prevent-spectator-teleport", true),
                r.getBoolean("can-chat", true),
                Set.copyOf(allowed),
                banSettings);
    }

    private static Revive readRevive(ConfigReader r, Hearts hearts) {
        GameMode gameMode = r.getEnum("gamemode", GameMode.class, GameMode.SURVIVAL);
        if (gameMode == GameMode.SPECTATOR) {
            r.problem("gamemode", "SPECTATOR is not a valid revive game mode, using SURVIVAL");
            gameMode = GameMode.SURVIVAL;
        }
        ConfigReader p = r.child("player-revive");
        PlayerRevive playerRevive = new PlayerRevive(
                p.getBoolean("enabled", true),
                p.getDouble("cost-hearts", 2.0, 0.0, hearts.hardLimit()),
                p.getDouble("money-cost", 0.0, 0.0, 1_000_000_000_000d),
                p.getDuration("cooldown", "10m", false),
                p.getDouble("minimum-remaining-hearts", 3.0, 0.5, hearts.hardLimit()),
                p.getBoolean("confirm", true));
        double reviveHearts = r.getDouble("hearts", 3.0, 0.5, hearts.hardLimit());
        return new Revive(reviveHearts, gameMode, r.getEnum("teleport", TeleportMode.class, TeleportMode.WORLD_SPAWN),
                r.getString("spawn-world", ""), r.getBoolean("heal", true), r.getBoolean("broadcast", true), playerRevive);
    }

    private static Withdraw readWithdraw(ConfigReader r, Hearts hearts) {
        double min = r.getDouble("minimum-amount", 1.0, 0.5, hearts.hardLimit());
        double max = r.getDouble("maximum-amount", 10.0, 0.5, hearts.hardLimit());
        if (max < min) {
            r.problem("maximum-amount", "lower than minimum-amount, using minimum-amount");
            max = min;
        }
        return new Withdraw(r.getBoolean("enabled", true), min, max,
                r.getDouble("minimum-remaining-hearts", 2.0, 0.5, hearts.hardLimit()),
                r.getEnum("full-inventory", FullInventory.class, FullInventory.DROP),
                r.getDuration("cooldown", "3s", false));
    }

    private static HeartDrop readHeartDrop(ConfigReader r) {
        Set<String> worlds = new HashSet<>();
        for (String world : r.getStringList("worlds")) {
            worlds.add(world.toLowerCase(Locale.ROOT));
        }
        return new HeartDrop(r.getBoolean("enabled", false), r.getString("item", "small_heart"),
                r.getDouble("chance", 25.0, 0.0, 100.0), r.getInt("amount", 1, 1, 64),
                r.getEnum("behavior", DropBehavior.class, DropBehavior.GROUND), r.getString("permission", ""),
                Set.copyOf(worlds), r.getBoolean("require-legit-kill", true));
    }

    private static AntiExploit readAntiExploit(ConfigReader r) {
        return new AntiExploit(
                r.getBoolean("same-ip", true),
                r.getBoolean("repeated-kill", true),
                r.getSecondsAsMillis("repeated-kill-delay", 300),
                r.getBoolean("prevent-alt-farming", true),
                (long) (r.getDouble("alt-window-hours", 24, 0, 24 * 365) * 3_600_000L),
                r.getBoolean("rapid-kills.enabled", true),
                r.getInt("rapid-kills.max-kills", 5, 1, 1000),
                r.getSecondsAsMillis("rapid-kills.window-seconds", 120),
                r.getBoolean("victim-loses-hearts-when-blocked", false),
                r.getBoolean("notify-staff", true),
                (long) r.getDouble("duplicate-death-window-ms", 1000, 0, 60_000),
                (long) r.getDouble("command-cooldown-ms", 400, 0, 60_000),
                r.getBoolean("block-creative-item-use", true));
    }

    private static Combat readCombat(ConfigReader r) {
        Set<CombatAction> blocked = EnumSet.noneOf(CombatAction.class);
        for (String entry : r.getStringList("blocked-actions")) {
            try {
                blocked.add(CombatAction.valueOf(entry.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                r.problem("blocked-actions", "unknown action '" + entry + "'");
            }
        }
        return new Combat(r.getSecondsAsMillis("tag-duration", 15), r.getBoolean("punish-combat-logging", true),
                r.getBoolean("use-external-plugins", true), Collections.unmodifiableSet(blocked));
    }

    private static ResourcePack readResourcePack(ConfigReader r) {
        String sha1 = r.getString("sha1", "").trim();
        if (!sha1.isEmpty() && !sha1.matches("[0-9a-fA-F]{40}")) {
            r.problem("sha1", "must be 40 hexadecimal characters, ignoring it");
            sha1 = "";
        }
        return new ResourcePack(r.getBoolean("export-on-startup", true), r.getBoolean("send-on-join", false),
                r.getString("url", "").trim(), sha1, r.getBoolean("required", false),
                r.getBoolean("host.enabled", false), r.getInt("host.port", 8163, 1, 65535),
                r.getString("host.public-address", "127.0.0.1").trim());
    }

    private static Map<String, List<String>> readAliases(ConfigReader r) {
        Map<String, List<String>> aliases = new LinkedHashMap<>();
        ConfigurationSection section = r.section();
        for (String sub : section.getKeys(false)) {
            List<String> names = new ArrayList<>();
            for (String name : r.getStringList(sub)) {
                String normalized = name.trim().toLowerCase(Locale.ROOT);
                if (normalized.matches("[a-z0-9_-]{1,32}")) {
                    names.add(normalized);
                } else if (!normalized.isEmpty()) {
                    r.problem(sub, "invalid command name '" + name + "'");
                }
            }
            aliases.put(sub.toLowerCase(Locale.ROOT), List.copyOf(names));
        }
        return Collections.unmodifiableMap(aliases);
    }

    private static Integrations readIntegrations(ConfigReader r) {
        return new Integrations(r.getBoolean("placeholderapi", true), r.getBoolean("parse-placeholders-in-messages", true),
                r.getBoolean("luckperms", true), r.getBoolean("vault", true), r.getBoolean("combatlogx", true),
                r.getBoolean("deluxecombat", true), r.getBoolean("pvpmanager", true),
                r.getString("holograms", "AUTO").trim().toUpperCase(Locale.ROOT));
    }

    private static Locale parseLocale(String tag) {
        String normalized = tag == null ? "en_US" : tag.replace('-', '_');
        String[] parts = normalized.split("_");
        if (parts.length >= 2) {
            return Locale.of(parts[0], parts[1]);
        }
        return Locale.of(parts[0]);
    }
}
