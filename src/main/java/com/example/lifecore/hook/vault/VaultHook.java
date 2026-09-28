package com.example.lifecore.hook.vault;

import com.example.lifecore.hook.Reflection;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;

/**
 * Vault economy bridge (reflection based; Vault is optional and not needed at compile time).
 * The economy provider is resolved lazily because economy plugins often enable after LifeCore.
 */
public final class VaultHook {

    private final Class<?> economyClass;
    private final Method has;
    private final Method withdraw;
    private final Method format;
    private Object economy;
    private Method transactionSuccess;

    public VaultHook(Plugin vault) throws ReflectiveOperationException {
        this.economyClass = Class.forName("net.milkbowl.vault.economy.Economy", true, vault.getClass().getClassLoader());
        this.has = economyClass.getMethod("has", OfflinePlayer.class, double.class);
        this.withdraw = economyClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
        this.format = economyClass.getMethod("format", double.class);
    }

    private Object economy() {
        if (economy == null) {
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(economyClass);
            if (registration != null) {
                economy = registration.getProvider();
            }
        }
        return economy;
    }

    public boolean isAvailable() {
        return economy() != null;
    }

    public boolean has(OfflinePlayer player, double amount) {
        Object eco = economy();
        if (eco == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(has.invoke(eco, player, amount));
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        Object eco = economy();
        if (eco == null) {
            return false;
        }
        try {
            Object response = withdraw.invoke(eco, player, amount);
            if (response == null) {
                return false;
            }
            if (transactionSuccess == null) {
                transactionSuccess = Reflection.findMethod(response.getClass(), "transactionSuccess");
            }
            return Boolean.TRUE.equals(transactionSuccess.invoke(response));
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    public String format(double amount) {
        Object eco = economy();
        if (eco != null) {
            try {
                Object formatted = format.invoke(eco, amount);
                if (formatted != null) {
                    return formatted.toString();
                }
            } catch (ReflectiveOperationException ignored) {
                // fall through
            }
        }
        return String.format(java.util.Locale.ROOT, "%.2f", amount);
    }
}
