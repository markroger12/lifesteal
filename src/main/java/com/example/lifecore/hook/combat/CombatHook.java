package com.example.lifecore.hook.combat;

import org.bukkit.entity.Player;

/**
 * Bridge to an external combat-tagging plugin.
 */
public interface CombatHook {

    String name();

    boolean isInCombat(Player player);
}
