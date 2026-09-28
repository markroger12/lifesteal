package com.example.lifecore.listener;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.manager.lifesteal.DeathCause;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Tags players in PvP combat (direct hits, projectiles, tamed animals, TNT).
 */
public final class CombatListener implements Listener {

    private final LifeCorePlugin plugin;

    public CombatListener(LifeCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || event.getFinalDamage() <= 0) {
            return;
        }
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        if (victim.hasMetadata("NPC") || attacker.hasMetadata("NPC")) {
            return;
        }
        plugin.combat().tag(victim.getUniqueId(), attacker.getUniqueId());
    }

    private static Player resolveAttacker(Entity damager) {
        Entity source = DeathCause.resolveSource(damager);
        if (source instanceof Player player) {
            return player;
        }
        Player owner = DeathCause.tamedOwner(source);
        if (owner != null) {
            return owner;
        }
        if (source instanceof TNTPrimed tnt && tnt.getSource() instanceof Player igniter) {
            return igniter;
        }
        return null;
    }
}
