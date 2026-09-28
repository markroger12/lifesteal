package com.example.lifecore.hook.combat;

import com.example.lifecore.hook.Reflection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * DeluxeCombat integration: {@code new DeluxeCombatAPI().isInCombat(Player)}.
 */
public final class DeluxeCombatHook implements CombatHook {

    private final Object api;
    private final Method isInCombat;

    public DeluxeCombatHook(Plugin plugin) throws ReflectiveOperationException {
        Class<?> apiClass = Class.forName("nl.marido.deluxecombat.api.DeluxeCombatAPI", true, plugin.getClass().getClassLoader());
        this.api = apiClass.getConstructor().newInstance();
        this.isInCombat = Reflection.findMethod(apiClass, "isInCombat", Player.class);
    }

    @Override
    public String name() {
        return "DeluxeCombat";
    }

    @Override
    public boolean isInCombat(Player player) {
        try {
            return Boolean.TRUE.equals(isInCombat.invoke(api, player));
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
