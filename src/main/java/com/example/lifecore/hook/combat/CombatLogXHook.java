package com.example.lifecore.hook.combat;

import com.example.lifecore.hook.Reflection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * CombatLogX (v11+) integration: {@code ICombatLogX#getCombatManager().isInCombat(Player)}.
 */
public final class CombatLogXHook implements CombatHook {

    private final Object combatManager;
    private final Method isInCombat;

    public CombatLogXHook(Plugin plugin) throws ReflectiveOperationException {
        Method getCombatManager = Reflection.findMethod(plugin.getClass(), "getCombatManager");
        this.combatManager = getCombatManager.invoke(plugin);
        if (combatManager == null) {
            throw new IllegalStateException("CombatLogX combat manager is not available");
        }
        this.isInCombat = Reflection.findMethod(combatManager.getClass(), "isInCombat", Player.class);
    }

    @Override
    public String name() {
        return "CombatLogX";
    }

    @Override
    public boolean isInCombat(Player player) {
        try {
            return Boolean.TRUE.equals(isInCombat.invoke(combatManager, player));
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
