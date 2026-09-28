package com.example.lifecore.manager.lifesteal;

import com.example.lifecore.util.compat.Compat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * A resolved death cause.
 *
 * @param primary      the key shown to players and stored as last death cause (e.g. LAVA, BLAZE, PLAYER)
 * @param lookupChain  keys checked in order against death.causes (e.g. [BLAZE, ENTITY])
 * @param mob          true if a non-player mob caused the death
 */
public record DeathCause(String primary, List<String> lookupChain, boolean mob) {

    public static final DeathCause PLAYER = new DeathCause("PLAYER", List.of("PLAYER"), false);
    public static final DeathCause OTHER = new DeathCause("OTHER", List.of("OTHER"), false);

    private static final Map<String, String> DAMAGE_CAUSES = Map.ofEntries(
            Map.entry("FALL", "FALL"),
            Map.entry("DROWNING", "DROWNING"),
            Map.entry("FIRE", "FIRE"),
            Map.entry("FIRE_TICK", "FIRE"),
            Map.entry("HOT_FLOOR", "FIRE"),
            Map.entry("CAMPFIRE", "FIRE"),
            Map.entry("LAVA", "LAVA"),
            Map.entry("VOID", "VOID"),
            Map.entry("BLOCK_EXPLOSION", "EXPLOSION"),
            Map.entry("ENTITY_EXPLOSION", "EXPLOSION"),
            Map.entry("PROJECTILE", "PROJECTILE"),
            Map.entry("SUFFOCATION", "SUFFOCATION"),
            Map.entry("LIGHTNING", "LIGHTNING"),
            Map.entry("STARVATION", "STARVATION"),
            Map.entry("FREEZE", "FREEZE"),
            Map.entry("MAGIC", "MAGIC"),
            Map.entry("POISON", "MAGIC"),
            Map.entry("WITHER", "WITHER_EFFECT"),
            Map.entry("CONTACT", "CONTACT"),
            Map.entry("CRAMMING", "CRAMMING"),
            Map.entry("FLY_INTO_WALL", "FLY_INTO_WALL"),
            Map.entry("DRAGON_BREATH", "DRAGON_BREATH"),
            Map.entry("SONIC_BOOM", "SONIC_BOOM"),
            Map.entry("KILL", "KILL_COMMAND"),
            Map.entry("SUICIDE", "KILL_COMMAND"),
            Map.entry("WORLD_BORDER", "SUFFOCATION"),
            Map.entry("ENTITY_ATTACK", "ENTITY"),
            Map.entry("ENTITY_SWEEP_ATTACK", "ENTITY"),
            Map.entry("THORNS", "ENTITY"));

    /**
     * Resolves the cause of a death.
     *
     * @param last   the victim's last damage event (may be null)
     * @param killer the credited killer (null for non-PvP deaths)
     */
    public static DeathCause resolve(@Nullable EntityDamageEvent last, @Nullable Player killer) {
        if (killer != null) {
            return PLAYER;
        }
        if (last == null) {
            return OTHER;
        }
        String cause = String.valueOf(last.getCause());
        if (last instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = resolveSource(byEntity.getDamager());
            if (damager instanceof LivingEntity && !(damager instanceof Player)) {
                String mob = Compat.keyName(damager.getType());
                return new DeathCause(mob, List.of(mob, "ENTITY"), true);
            }
        }
        return fromDamageCause(cause);
    }

    /** Maps a Bukkit DamageCause name to a LifeCore cause key. */
    public static DeathCause fromDamageCause(String damageCause) {
        String mapped = DAMAGE_CAUSES.getOrDefault(damageCause, "OTHER");
        if (mapped.equals("OTHER")) {
            return OTHER;
        }
        return new DeathCause(mapped, List.of(mapped, "OTHER"), false);
    }

    /** Resolves projectiles to their shooter and tamed animals to nothing special (the animal itself). */
    public static Entity resolveSource(Entity damager) {
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Entity entity) {
                return entity;
            }
        }
        return damager;
    }

    /** @return the owning player of a tamed animal, or null. */
    public static @Nullable Player tamedOwner(Entity entity) {
        if (entity instanceof Tameable tameable && tameable.getOwner() instanceof Player owner) {
            return owner;
        }
        return null;
    }
}
