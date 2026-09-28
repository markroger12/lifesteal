package com.example.lifecore.manager.heart;

import com.example.lifecore.configuration.settings.LifeCoreSettings;
import com.example.lifecore.model.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Resolves per-player heart caps and permission based multipliers.
 * <p>
 * Permission caps are cached per player and invalidated on join, reload and (with LuckPerms)
 * whenever a player's permissions change, so heart checks never iterate permissions repeatedly.
 */
public final class HeartCapResolver {

    public static final String NUMERIC_CAP_PREFIX = "lifecore.heartcap.";

    private final Supplier<LifeCoreSettings> settings;
    private final Map<UUID, Double> permissionCaps = new ConcurrentHashMap<>();

    public HeartCapResolver(Supplier<LifeCoreSettings> settings) {
        this.settings = settings;
    }

    public void invalidate(UUID uuid) {
        permissionCaps.remove(uuid);
    }

    public void invalidateAll() {
        permissionCaps.clear();
    }

    /** Maximum hearts for an online player. */
    public double maxHearts(Player player, PlayerData data) {
        LifeCoreSettings.Hearts hearts = settings.get().hearts;
        double override = data == null ? -1 : data.getMaxHeartsOverride();
        if (override > 0) {
            return Math.min(override, hearts.hardLimit());
        }
        if (player == null) {
            return hearts.maximum();
        }
        return permissionCaps.computeIfAbsent(player.getUniqueId(), id -> computePermissionCap(player, hearts));
    }

    /** Maximum hearts when no online player is available (override or configured maximum). */
    public double maxHeartsOffline(PlayerData data) {
        LifeCoreSettings.Hearts hearts = settings.get().hearts;
        double override = data == null ? -1 : data.getMaxHeartsOverride();
        if (override > 0) {
            return Math.min(override, hearts.hardLimit());
        }
        Double cached = data == null ? null : permissionCaps.get(data.getUniqueId());
        return cached != null ? cached : hearts.maximum();
    }

    private double computePermissionCap(Player player, LifeCoreSettings.Hearts hearts) {
        double cap = hearts.maximum();
        if (!hearts.permissionCapsEnabled()) {
            return cap;
        }
        for (LifeCoreSettings.PermissionValue tier : hearts.capTiers()) {
            if (tier.value() > cap && player.hasPermission(tier.permission())) {
                cap = tier.value();
            }
        }
        if (hearts.numericPermissions()) {
            for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
                if (!info.getValue()) {
                    continue;
                }
                String permission = info.getPermission();
                if (permission.length() > NUMERIC_CAP_PREFIX.length() && permission.regionMatches(true, 0, NUMERIC_CAP_PREFIX, 0, NUMERIC_CAP_PREFIX.length())) {
                    String suffix = permission.substring(NUMERIC_CAP_PREFIX.length());
                    try {
                        double value = Double.parseDouble(suffix);
                        if (Double.isFinite(value) && value > cap) {
                            cap = value;
                        }
                    } catch (NumberFormatException ignored) {
                        // named tier such as lifecore.heartcap.vip - handled above
                    }
                }
            }
        }
        return Math.min(cap, hearts.hardLimit());
    }

    /** Highest applicable gain multiplier (1.0 if none). */
    public double gainMultiplier(Player player) {
        double best = 1.0;
        boolean matched = false;
        for (LifeCoreSettings.PermissionValue entry : settings.get().hearts.gainMultipliers()) {
            if (player.hasPermission(entry.permission()) && (!matched || entry.value() > best)) {
                best = entry.value();
                matched = true;
            }
        }
        return best;
    }

    /** Lowest applicable loss multiplier (1.0 if none). */
    public double lossMultiplier(Player player) {
        double best = 1.0;
        boolean matched = false;
        for (LifeCoreSettings.PermissionValue entry : settings.get().hearts.lossMultipliers()) {
            if (player.hasPermission(entry.permission()) && (!matched || entry.value() < best)) {
                best = entry.value();
                matched = true;
            }
        }
        return best;
    }
}
