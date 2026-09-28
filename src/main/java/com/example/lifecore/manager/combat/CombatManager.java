package com.example.lifecore.manager.combat;

import com.example.lifecore.configuration.settings.LifeCoreSettings;
import com.example.lifecore.hook.combat.CombatHook;
import com.example.lifecore.util.LifeLogger;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Internal combat tagging plus optional external combat plugin hooks.
 * <p>
 * The internal tracker also remembers the last player attacker of every player, which powers
 * indirect kill credit (knocked into lava/void) and combat-log kill credit.
 */
public final class CombatManager {

    private record Attack(UUID attacker, long time) {
    }

    private final Supplier<LifeCoreSettings> settings;
    private final LifeLogger logger;
    private final LongSupplier clock;
    private final Map<UUID, Long> taggedUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Attack> lastAttack = new ConcurrentHashMap<>();
    private final Set<UUID> kicked = ConcurrentHashMap.newKeySet();
    private final List<CombatHook> hooks = new CopyOnWriteArrayList<>();

    public CombatManager(Supplier<LifeCoreSettings> settings, LifeLogger logger, LongSupplier clock) {
        this.settings = settings;
        this.logger = logger;
        this.clock = clock;
    }

    public void registerHook(CombatHook hook) {
        hooks.add(hook);
        logger.info("[hooks] Combat integration enabled: " + hook.name());
    }

    public void clearHooks() {
        hooks.clear();
    }

    public List<CombatHook> hooks() {
        return List.copyOf(hooks);
    }

    public void tag(UUID victim, UUID attacker) {
        long now = clock.getAsLong();
        long until = now + settings.get().combat.tagMillis();
        taggedUntil.put(victim, until);
        taggedUntil.put(attacker, until);
        lastAttack.put(victim, new Attack(attacker, now));
    }

    public boolean isTaggedInternally(UUID player) {
        Long until = taggedUntil.get(player);
        return until != null && until > clock.getAsLong();
    }

    public long remainingTag(UUID player) {
        Long until = taggedUntil.get(player);
        return until == null ? 0 : Math.max(0, until - clock.getAsLong());
    }

    public boolean isInCombat(Player player) {
        if (isTaggedInternally(player.getUniqueId())) {
            return true;
        }
        if (!settings.get().combat.useExternal()) {
            return false;
        }
        for (CombatHook hook : hooks) {
            try {
                if (hook.isInCombat(player)) {
                    return true;
                }
            } catch (RuntimeException | LinkageError ex) {
                logger.warn("[hooks] " + hook.name() + " combat check failed - hook disabled: " + ex);
                hooks.remove(hook);
            }
        }
        return false;
    }

    /** @return true if the action is blocked because the player is in combat. */
    public boolean isBlocked(Player player, LifeCoreSettings.CombatAction action) {
        return settings.get().combat.blockedActions().contains(action) && isInCombat(player);
    }

    /** @return the player who last damaged the victim within the window. */
    public Optional<UUID> recentAttacker(UUID victim, long windowMillis) {
        Attack attack = lastAttack.get(victim);
        if (attack == null || clock.getAsLong() - attack.time() > windowMillis) {
            return Optional.empty();
        }
        return Optional.of(attack.attacker());
    }

    public void markKicked(UUID player) {
        kicked.add(player);
    }

    /** @return true (once) if the player's disconnect was a kick rather than a logout. */
    public boolean consumeKicked(UUID player) {
        return kicked.remove(player);
    }

    public void clear(UUID player) {
        taggedUntil.remove(player);
        lastAttack.remove(player);
    }

    public void purge() {
        long now = clock.getAsLong();
        taggedUntil.values().removeIf(until -> until < now);
        lastAttack.values().removeIf(attack -> now - attack.time() > 300_000L);
    }
}
