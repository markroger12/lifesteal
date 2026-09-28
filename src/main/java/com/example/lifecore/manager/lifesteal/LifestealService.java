package com.example.lifecore.manager.lifesteal;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.api.HeartChangeReason;
import com.example.lifecore.api.HeartChangeResult;
import com.example.lifecore.configuration.settings.LifeCoreSettings;
import com.example.lifecore.configuration.settings.WorldRules;
import com.example.lifecore.event.PlayerLifestealEvent;
import com.example.lifecore.item.heart.HeartItemDefinition;
import com.example.lifecore.manager.heart.HeartManager;
import com.example.lifecore.manager.heart.HeartMath;
import com.example.lifecore.model.PlayerData;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Processes deaths: PvP heart transfer, natural heart loss, elimination, rewards and feedback.
 * <p>
 * Every death is processed exactly once (see {@link com.example.lifecore.manager.antiexploit.DeathGuard}),
 * statistics and heart changes are applied to the cached data in one pass on the victim's thread,
 * and both players are persisted immediately afterwards.
 */
public final class LifestealService {

    private final LifeCorePlugin plugin;

    public LifestealService(LifeCorePlugin plugin) {
        this.plugin = plugin;
    }

    private LifeCoreSettings settings() {
        return plugin.settings();
    }

    public void handleDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (isNpc(victim)) {
            return;
        }
        PlayerData victimData = plugin.players().get(victim);
        if (victimData == null) {
            plugin.log().warn("death", "No data loaded for dying player - death ignored", "player", victim.getName());
            return;
        }
        if (victimData.isEliminated()) {
            return;
        }
        if (plugin.antiExploit().isDuplicateDeath(victim.getUniqueId())) {
            plugin.log().debug("death", "Ignored duplicate death event", "player", victim.getName());
            return;
        }
        long now = System.currentTimeMillis();
        WorldRules rules = plugin.worlds().get(victim.getWorld());
        Player killer = resolveKiller(victim);
        DeathCause cause = DeathCause.resolve(victim.getLastDamageCause(), killer);
        Location location = victim.getLocation();

        if (!rules.enabled()) {
            victimData.recordDeath(cause.primary(), killer == null ? "" : killer.getName(), now);
            PlayerData killerData = killer == null ? null : plugin.players().get(killer);
            if (killerData != null) {
                killerData.addKill();
            }
            plugin.players().save(victimData);
            return;
        }
        if (killer != null && settings().kill.enabled() && rules.playerKills()) {
            PlayerData killerData = plugin.players().get(killer);
            if (killerData != null) {
                processKill(victim, victimData, killer, killerData, rules, location, now);
                return;
            }
        }
        processNaturalDeath(victim, victimData, cause, killer, rules, now);
    }

    /** Resolves the credited killer, including indirect kills via the combat tracker. */
    public @Nullable Player resolveKiller(Player victim) {
        Player killer = victim.getKiller();
        if (killer == null && settings().kill.indirectKills()) {
            Optional<UUID> attacker = plugin.combat().recentAttacker(victim.getUniqueId(), settings().kill.indirectWindowMillis());
            if (attacker.isPresent()) {
                killer = Bukkit.getPlayer(attacker.get());
            }
        }
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId()) || isNpc(killer)) {
            return null;
        }
        return killer;
    }

    // ------------------------------------------------------------------ PvP

    private void processKill(Player victim, PlayerData victimData, Player killer, PlayerData killerData, WorldRules rules,
                             Location location, long now) {
        LifeCoreSettings s = settings();
        boolean half = s.hearts.halfHearts();
        victimData.recordDeath("PLAYER", killer.getName(), now);
        killerData.addKill();

        Optional<String> blocked = killer.hasPermission("lifecore.bypass.antiexploit") ? Optional.empty()
                : plugin.antiExploit().evaluateKill(killer.getUniqueId(), victim.getUniqueId());

        double loss = baseKillLoss(rules);
        if (rules.instantElimination()) {
            loss = victimData.getHearts();
        }
        loss *= plugin.caps().lossMultiplier(victim);
        if (!rules.lifestealActive() || victim.hasPermission("lifecore.bypass.loss")) {
            loss = 0;
        }
        double gain = rules.killerGain() >= 0 ? rules.killerGain() : s.kill.killerGain();
        gain *= plugin.caps().gainMultiplier(killer);
        if (!rules.lifestealActive() || !rules.heartGain() || (killerData.isEliminated() && !s.kill.eliminatedKillersGain())) {
            gain = 0;
        }
        if (blocked.isPresent()) {
            gain = 0;
            if (!s.antiExploit.victimLosesWhenBlocked()) {
                loss = 0;
            }
        }
        loss = HeartMath.round(loss, half);
        gain = HeartMath.round(gain, half);

        PlayerLifestealEvent event = new PlayerLifestealEvent(killer, victim, gain, loss, blocked.orElse(null));
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            plugin.players().save(victimData);
            plugin.players().save(killerData);
            return;
        }
        gain = HeartMath.round(event.getKillerGain(), half);
        loss = HeartMath.round(event.getVictimLoss(), half);

        boolean eliminationAllowed = rules.elimination();
        HeartChangeResult lossResult = loss > 0
                ? plugin.hearts().remove(victimData, victim, loss, HeartChangeReason.KILL_LOSS,
                new HeartManager.ChangeOptions(true, false, eliminationAllowed, true, "PLAYER", killer.getName()))
                : HeartChangeResult.cancelled(victimData.getHearts(), 0, HeartChangeReason.KILL_LOSS);
        double actualLoss = lossResult.eliminated() ? lossResult.previous() : Math.max(0, -lossResult.delta());
        if (s.kill.gainMode() == LifeCoreSettings.GainMode.STOLEN && gain > 0) {
            gain = HeartMath.floor(actualLoss, half);
        }

        double gained = 0;
        boolean capped = false;
        if (gain > 0) {
            if (s.kill.rewardMode() == LifeCoreSettings.RewardMode.DIRECT) {
                HeartChangeResult gainResult = plugin.hearts().add(killerData, killer, gain, HeartChangeReason.KILL_REWARD,
                        HeartManager.ChangeOptions.standard());
                gained = Math.max(0, gainResult.delta());
                double overflow = gainResult.cancelled() ? 0 : gain - gained;
                capped = overflow > 0;
                if (capped && s.kill.atMaxHearts() == LifeCoreSettings.AtMaxMode.DROP_ITEM) {
                    dropHeartItems(s.kill.overflowItem(), overflow, location);
                }
            } else {
                dropHeartItems(s.kill.rewardItem(), gain, location);
            }
        }

        boolean legit = blocked.isEmpty();
        plugin.heartDrops().handleKill(killer, victim, rules, legit, location);
        if (legit) {
            plugin.antiExploit().recordRewardedKill(killer.getUniqueId(), victim.getUniqueId());
        }

        Placeholders ph = new Placeholders()
                .add("killer", killer.getName())
                .add("victim", victim.getName())
                .add("gained", plugin.messages().hearts(gained))
                .add("reward", plugin.messages().hearts(gain))
                .add("lost", plugin.messages().hearts(actualLoss))
                .add("killer_hearts", plugin.messages().hearts(killerData.getHearts()))
                .add("victim_hearts", plugin.messages().hearts(victimData.getHearts()))
                .add("max", plugin.messages().hearts(plugin.hearts().getMaxHearts(killerData, killer)));
        if (blocked.isPresent()) {
            String remaining = plugin.messages().duration(plugin.antiExploit().kills().repeatedKillRemaining(
                    killer.getUniqueId(), victim.getUniqueId(), s.antiExploit.repeatedKillDelayMillis()));
            String reason = Placeholders.of("remaining", remaining)
                    .apply(plugin.messages().raw("anti-exploit.reasons." + blocked.get()));
            ph.add("remaining", remaining).add("reason", reason);
            plugin.messages().send(killer, "kill.blocked", ph);
            if (s.antiExploit.notifyStaff()) {
                plugin.messages().broadcastPermission("anti-exploit.staff-alert", ph, "lifecore.notify");
            }
            plugin.log().info("anti-exploit", "Kill reward blocked", "killer", killer.getName(), "victim", victim.getName(),
                    "rule", blocked.get());
        } else if (gain > 0 && s.kill.rewardMode() == LifeCoreSettings.RewardMode.ITEM) {
            plugin.messages().send(killer, "kill.killer-item", ph);
        } else if (capped) {
            plugin.messages().send(killer, "kill.killer-max", ph);
        } else if (gained > 0) {
            plugin.messages().send(killer, "kill.killer", ph);
        } else {
            plugin.messages().send(killer, "kill.killer-no-gain", ph);
        }
        plugin.sounds().play(killer, "kill");
        if (!lossResult.eliminated()) {
            plugin.messages().send(victim, actualLoss > 0 ? "kill.victim" : "kill.victim-no-loss", ph);
        }
        if (s.kill.broadcast() && legit) {
            plugin.messages().broadcast("kill.broadcast", ph);
        }
        plugin.players().save(victimData);
        plugin.players().save(killerData);
    }

    private double baseKillLoss(WorldRules rules) {
        if (rules.victimLoss() >= 0) {
            return rules.victimLoss();
        }
        LifeCoreSettings.Death death = settings().death;
        if (death.byCause()) {
            Double configured = death.lossFor("PLAYER");
            if (configured != null) {
                return configured;
            }
        }
        return settings().kill.victimLoss();
    }

    // ------------------------------------------------------------------ natural deaths

    private void processNaturalDeath(Player victim, PlayerData victimData, DeathCause cause, @Nullable Player killer,
                                     WorldRules rules, long now) {
        LifeCoreSettings s = settings();
        victimData.recordDeath(cause.primary(), killer == null ? "" : killer.getName(), now);
        if (killer != null) {
            PlayerData killerData = plugin.players().get(killer);
            if (killerData != null) {
                killerData.addKill();
                plugin.players().save(killerData);
            }
        }
        boolean losesHearts = rules.lifestealActive() && (rules.heartLoss() || rules.instantElimination())
                && (s.death.naturalDeathsLoseHearts() || rules.instantElimination())
                && (!cause.mob() || rules.mobLoss() || rules.instantElimination())
                && !victim.hasPermission("lifecore.bypass.loss");
        if (!losesHearts) {
            plugin.players().save(victimData);
            return;
        }
        double loss;
        if (rules.instantElimination()) {
            loss = victimData.getHearts();
        } else {
            loss = naturalLoss(cause) * rules.naturalLossMultiplier() * plugin.caps().lossMultiplier(victim);
        }
        loss = HeartMath.round(loss, s.hearts.halfHearts());
        if (loss <= 0) {
            plugin.players().save(victimData);
            return;
        }
        HeartChangeResult result = plugin.hearts().remove(victimData, victim, loss, HeartChangeReason.DEATH,
                new HeartManager.ChangeOptions(true, false, rules.elimination(), true, cause.primary(), ""));
        if (!result.eliminated() && result.changed()) {
            plugin.messages().send(victim, "death.natural", new Placeholders()
                    .add("cause", causeName(cause.primary()))
                    .add("lost", plugin.messages().hearts(-result.delta()))
                    .add("hearts", plugin.messages().hearts(victimData.getHearts())));
        }
        plugin.players().save(victimData);
    }

    /** @return hearts lost for a natural death cause according to config. */
    public double naturalLoss(DeathCause cause) {
        LifeCoreSettings.Death death = settings().death;
        if (!death.byCause()) {
            return death.defaultLoss();
        }
        for (String key : cause.lookupChain()) {
            Double configured = death.lossFor(key);
            if (configured != null) {
                return configured;
            }
        }
        return death.disabledCauses().contains(cause.primary()) ? 0 : death.defaultLoss();
    }

    /** Localised cause name from messages.yml (death-causes.KEY), falling back to a prettified key. */
    public String causeName(String key) {
        if (key == null || key.isEmpty()) {
            return plugin.messages().raw("general.none");
        }
        String messageKey = "death-causes." + key.toUpperCase(Locale.ROOT);
        if (plugin.messages().exists(messageKey)) {
            return plugin.messages().raw(messageKey);
        }
        String pretty = key.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(pretty.charAt(0)) + pretty.substring(1);
    }

    // ------------------------------------------------------------------ helpers

    private void dropHeartItems(String itemId, double hearts, Location location) {
        HeartItemDefinition definition = plugin.items().heart(itemId).orElse(null);
        if (definition == null) {
            plugin.log().warn("[kill] Heart item '" + itemId + "' does not exist in items.yml - no item dropped.");
            return;
        }
        int count = (int) Math.floor(hearts / definition.hearts() + 1e-9);
        if (count <= 0) {
            return;
        }
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        ItemStack stack = plugin.items().createHeartItem(definition, Math.min(count, 64));
        world.dropItemNaturally(location, stack);
    }

    public static boolean isNpc(Entity entity) {
        return entity.hasMetadata("NPC");
    }
}
