package com.example.lifecore.hook.combat;

import com.example.lifecore.hook.Reflection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * PvPManager integration supporting both the legacy ({@code getPlayerHandler()}) and the
 * current ({@code getPlayerManager()}) API: {@code manager.get(Player).isInCombat()}.
 */
public final class PvPManagerHook implements CombatHook {

    private final Object manager;
    private final Method get;
    private Method isInCombat;

    public PvPManagerHook(Plugin plugin) throws ReflectiveOperationException {
        Method accessor;
        try {
            accessor = Reflection.findMethod(plugin.getClass(), "getPlayerManager");
        } catch (NoSuchMethodException ex) {
            accessor = Reflection.findMethod(plugin.getClass(), "getPlayerHandler");
        }
        this.manager = accessor.invoke(plugin);
        if (manager == null) {
            throw new IllegalStateException("PvPManager player manager is not available");
        }
        this.get = Reflection.findMethod(manager.getClass(), "get", Player.class);
    }

    @Override
    public String name() {
        return "PvPManager";
    }

    @Override
    public boolean isInCombat(Player player) {
        try {
            Object combatPlayer = get.invoke(manager, player);
            if (combatPlayer == null) {
                return false;
            }
            Method method = isInCombat;
            if (method == null) {
                method = Reflection.findMethod(combatPlayer.getClass(), "isInCombat");
                isInCombat = method;
            }
            return Boolean.TRUE.equals(method.invoke(combatPlayer));
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
