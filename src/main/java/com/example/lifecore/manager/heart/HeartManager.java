package com.example.lifecore.manager.heart;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.api.HeartChangeReason;
import com.example.lifecore.api.HeartChangeResult;
import com.example.lifecore.configuration.settings.LifeCoreSettings;
import com.example.lifecore.configuration.settings.WorldRules;
import com.example.lifecore.event.PlayerHeartGainEvent;
import com.example.lifecore.event.PlayerHeartLossEvent;
import com.example.lifecore.model.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The single entry point for every heart modification.
 * <p>
 * All changes are validated (NaN/Infinity/negative rejected), rounded to the configured precision,
 * clamped between the minimum and the player's cap, announced through cancellable events and
 * finally reflected in the player's max-health attribute on the entity's owning thread.
 * Elimination is triggered from here when a loss reaches the elimination threshold.
 */
public final class HeartManager {

    private final LifeCorePlugin plugin;

    public HeartManager(LifeCorePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Options for a single change.
     *
     * @param fireEvents       fire PlayerHeartGain/LossEvent
     * @param ignoreCap        allow exceeding the player's cap (still limited by the hard limit)
     * @param allowElimination allow a loss to eliminate the player
     * @param feedback         play sounds/particles for the change
     * @param cause            elimination cause key when a loss eliminates
     * @param killerName       elimination killer name (may be empty)
     */
    public record ChangeOptions(boolean fireEvents, boolean ignoreCap, boolean allowElimination, boolean feedback,
                                String cause, String killerName) {

        public static ChangeOptions standard() {
            return new ChangeOptions(true, false, false, true, "", "");
        }

        public static ChangeOptions silent() {
            return new ChangeOptions(true, false, false, false, "", "");
        }

        public static ChangeOptions eliminating(String cause, String killerName) {
            return new ChangeOptions(true, false, true, true, cause, killerName == null ? "" : killerName);
        }

        public ChangeOptions withIgnoreCap(boolean ignore) {
            return new ChangeOptions(fireEvents, ignore, allowElimination, feedback, cause, killerName);
        }

        public ChangeOptions withFeedback(boolean enabled) {
            return new ChangeOptions(fireEvents, ignoreCap, allowElimination, enabled, cause, killerName);
        }
    }

    private LifeCoreSettings settings() {
        return plugin.settings();
    }

    // ------------------------------------------------------------------ queries

    public double getHearts(Player player) {
        PlayerData data = plugin.players().get(player);
        return data == null ? 0 : data.getHearts();
    }

    /** @return cached hearts or -1 if the player's data is not loaded. */
    public double getHearts(UUID uuid) {
        PlayerData data = plugin.players().get(uuid);
        return data == null ? -1 : data.getHearts();
    }

    public double getMaxHearts(Player player) {
        return plugin.caps().maxHearts(player, plugin.players().get(player));
    }

    public double getMaxHearts(PlayerData data, @Nullable Player online) {
        return online != null ? plugin.caps().maxHearts(online, data) : plugin.caps().maxHeartsOffline(data);
    }

    public double getMinHearts() {
        return settings().hearts.minimum();
    }

    public boolean hasMinimumHearts(Player player) {
        return HeartMath.lessOrEqual(getHearts(player), getMinHearts());
    }

    public boolean hasMaximumHearts(Player player) {
        return HeartMath.greaterOrEqual(getHearts(player), getMaxHearts(player));
    }

    // ------------------------------------------------------------------ convenience API (online players)

    public HeartChangeResult setHearts(Player player, double amount, HeartChangeReason reason) {
        return set(requireData(player), player, amount, reason, ChangeOptions.standard());
    }

    public HeartChangeResult addHearts(Player player, double amount, HeartChangeReason reason) {
        return add(requireData(player), player, amount, reason, ChangeOptions.standard());
    }

    public HeartChangeResult removeHearts(Player player, double amount, HeartChangeReason reason) {
        return remove(requireData(player), player, amount, reason, ChangeOptions.standard());
    }

    private PlayerData requireData(Player player) {
        PlayerData data = plugin.players().get(player);
        if (data == null) {
            throw new IllegalStateException("LifeCore data for " + player.getName() + " is not loaded");
        }
        return data;
    }

    // ------------------------------------------------------------------ core operations

    public HeartChangeResult add(PlayerData data, @Nullable Player online, double amount, HeartChangeReason reason, ChangeOptions options) {
        boolean half = settings().hearts.halfHearts();
        double requested = HeartMath.round(amount, half);
        double current = data.getHearts();
        if (requested <= 0 || data.isEliminated()) {
            return HeartChangeResult.cancelled(current, requested, reason);
        }
        if (options.fireEvents()) {
            PlayerHeartGainEvent event = new PlayerHeartGainEvent(data.getUniqueId(), data.getName(), current, requested, reason);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return HeartChangeResult.cancelled(current, requested, reason);
            }
            requested = HeartMath.round(event.getAmount(), half);
        }
        double cap = options.ignoreCap() ? settings().hearts.hardLimit() : Math.min(getMaxHearts(data, online), settings().hearts.hardLimit());
        HeartCalculator.GainOutcome outcome = HeartCalculator.gain(current, requested, cap);
        data.setHeartsInternal(outcome.newHearts());
        HeartChangeResult result = new HeartChangeResult(current, outcome.newHearts(), requested, reason, outcome.overflow() > 0, false, false);
        plugin.log().debug("hearts", "gain", "player", data.getName(), "reason", reason, "from", current, "to", outcome.newHearts());
        if (online != null && result.changed()) {
            applyAfterChange(online, outcome.applied(), options.feedback(), true);
        }
        return result;
    }

    public HeartChangeResult remove(PlayerData data, @Nullable Player online, double amount, HeartChangeReason reason, ChangeOptions options) {
        boolean half = settings().hearts.halfHearts();
        double requested = HeartMath.round(amount, half);
        double current = data.getHearts();
        if (requested <= 0 || data.isEliminated()) {
            return HeartChangeResult.cancelled(current, requested, reason);
        }
        if (options.fireEvents()) {
            PlayerHeartLossEvent event = new PlayerHeartLossEvent(data.getUniqueId(), data.getName(), current, requested, reason);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return HeartChangeResult.cancelled(current, requested, reason);
            }
            requested = HeartMath.round(event.getAmount(), half);
        }
        boolean eliminationEnabled = options.allowElimination() && eliminationAllowed(online);
        HeartCalculator.LossOutcome outcome = HeartCalculator.loss(current, requested, getMinHearts(),
                eliminationEnabled, settings().elimination.threshold());
        if (outcome.eliminate()) {
            boolean eliminated = plugin.elimination().eliminate(data, online, options.cause(), options.killerName());
            if (eliminated) {
                plugin.log().debug("hearts", "loss eliminated player", "player", data.getName(), "reason", reason);
                return new HeartChangeResult(current, 0, requested, reason, false, false, true);
            }
            // Elimination was cancelled by an event listener: keep the player at the minimum instead.
            outcome = HeartCalculator.loss(current, requested, getMinHearts(), false, 0);
        }
        data.setHeartsInternal(outcome.newHearts());
        HeartChangeResult result = new HeartChangeResult(current, outcome.newHearts(), requested, reason,
                outcome.applied() < requested, false, false);
        plugin.log().debug("hearts", "loss", "player", data.getName(), "reason", reason, "from", current, "to", outcome.newHearts());
        if (online != null && result.changed()) {
            applyAfterChange(online, outcome.applied(), options.feedback(), false);
        }
        return result;
    }

    public HeartChangeResult set(PlayerData data, @Nullable Player online, double value, HeartChangeReason reason, ChangeOptions options) {
        boolean half = settings().hearts.halfHearts();
        double target = HeartMath.round(value, half);
        double current = data.getHearts();
        if (data.isEliminated()) {
            return HeartChangeResult.cancelled(current, target, reason);
        }
        if (target > current) {
            return add(data, online, target - current, reason, options);
        }
        if (target < current) {
            return remove(data, online, current - target, reason, options);
        }
        return new HeartChangeResult(current, current, target, reason, false, false, false);
    }

    private boolean eliminationAllowed(@Nullable Player online) {
        if (!settings().elimination.enabled()) {
            return false;
        }
        if (online == null) {
            return true;
        }
        WorldRules rules = plugin.worlds().get(online.getWorld());
        return rules.enabled() && rules.elimination();
    }

    // ------------------------------------------------------------------ attributes

    private void applyAfterChange(Player player, double applied, boolean feedback, boolean gain) {
        plugin.scheduler().runAtEntity(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            applyAttribute(player);
            if (gain && settings().hearts.healOnGain() && !player.isDead()) {
                double max = maxHealth(player);
                player.setHealth(Math.min(max, player.getHealth() + applied * 2.0));
            }
            if (feedback) {
                plugin.sounds().play(player, gain ? "heart-gain" : "heart-loss");
                plugin.particles().play(gain ? "heart-gain" : "heart-loss", player.getLocation());
            }
        });
    }

    /**
     * Synchronises a player's max-health attribute with their stored hearts.
     * Must run on the player's owning thread.
     */
    public void applyAttribute(Player player) {
        PlayerData data = plugin.players().get(player);
        if (data == null) {
            return;
        }
        AttributeInstance attribute = player.getAttribute(plugin.maxHealthAttribute());
        if (attribute == null) {
            return;
        }
        double hearts = data.isEliminated() ? Math.max(settings().hearts.minimum(), 1.0) : data.getHearts();
        double health = HeartMath.toHealthPoints(hearts);
        if (Math.abs(attribute.getBaseValue() - health) > 1e-6) {
            attribute.setBaseValue(health);
        }
        double max = attribute.getValue();
        if (!player.isDead() && player.getHealth() > max) {
            player.setHealth(Math.max(0.5, max));
        }
    }

    public double maxHealth(Player player) {
        AttributeInstance attribute = player.getAttribute(plugin.maxHealthAttribute());
        return attribute == null ? 20.0 : attribute.getValue();
    }

    /** Clamps a player above their cap back down (used on join / permission changes). */
    public void enforceCap(Player player) {
        PlayerData data = plugin.players().get(player);
        if (data == null || data.isEliminated()) {
            return;
        }
        double cap = getMaxHearts(player);
        if (data.getHearts() > cap + 1e-9) {
            double before = data.getHearts();
            data.setHeartsInternal(cap);
            plugin.log().debug("hearts", "cap enforced", "player", player.getName(), "from", before, "to", cap);
            plugin.messages().send(player, "hearts.cap-enforced", com.example.lifecore.util.text.Placeholders.of(
                    "max", plugin.messages().hearts(cap), "previous", plugin.messages().hearts(before)));
        }
        applyAttribute(player);
    }
}
