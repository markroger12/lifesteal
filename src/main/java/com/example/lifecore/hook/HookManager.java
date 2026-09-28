package com.example.lifecore.hook;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.configuration.settings.LifeCoreSettings;
import com.example.lifecore.hook.combat.CombatHook;
import com.example.lifecore.hook.combat.CombatLogXHook;
import com.example.lifecore.hook.combat.DeluxeCombatHook;
import com.example.lifecore.hook.combat.PvPManagerHook;
import com.example.lifecore.hook.hologram.DecentHologramsProvider;
import com.example.lifecore.hook.hologram.HologramProvider;
import com.example.lifecore.hook.hologram.NativeHologramProvider;
import com.example.lifecore.hook.luckperms.LuckPermsHook;
import com.example.lifecore.hook.placeholderapi.PlaceholderAPIHook;
import com.example.lifecore.hook.vault.VaultHook;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Detects and manages optional integrations. Every integration is a soft dependency: if a plugin
 * is missing, disabled or its API changed, LifeCore logs it and keeps working without it.
 */
public final class HookManager {

    private final LifeCorePlugin plugin;
    private PlaceholderAPIHook placeholderApi;
    private LuckPermsHook luckPerms;
    private VaultHook vault;
    private final List<String> active = new ArrayList<>();

    public HookManager(LifeCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void enableAll() {
        disableAll();
        LifeCoreSettings.Integrations config = plugin.settings().integrations;
        PluginManager pm = Bukkit.getPluginManager();

        if (config.placeholderApi() && pm.isPluginEnabled("PlaceholderAPI")) {
            attempt("PlaceholderAPI", () -> {
                placeholderApi = new PlaceholderAPIHook(plugin);
                placeholderApi.register();
            });
        }
        if (config.luckPerms() && pm.isPluginEnabled("LuckPerms")) {
            attempt("LuckPerms", () -> luckPerms = new LuckPermsHook(plugin));
        }
        if (config.vault() && pm.isPluginEnabled("Vault")) {
            Plugin vaultPlugin = pm.getPlugin("Vault");
            attempt("Vault", () -> {
                try {
                    vault = new VaultHook(vaultPlugin);
                } catch (ReflectiveOperationException ex) {
                    throw new IllegalStateException(ex);
                }
            });
        }
        plugin.combat().clearHooks();
        if (config.combatLogX()) {
            registerCombat("CombatLogX", p -> new CombatLogXHook(p));
        }
        if (config.deluxeCombat()) {
            registerCombat("DeluxeCombat", p -> new DeluxeCombatHook(p));
        }
        if (config.pvpManager()) {
            registerCombat("PvPManager", p -> new PvPManagerHook(p));
        }
    }

    @FunctionalInterface
    private interface CombatFactory {
        CombatHook create(Plugin plugin) throws ReflectiveOperationException;
    }

    private void registerCombat(String name, CombatFactory factory) {
        Plugin target = Bukkit.getPluginManager().getPlugin(name);
        if (target == null || !target.isEnabled()) {
            return;
        }
        attempt(name, () -> {
            try {
                plugin.combat().registerHook(factory.create(target));
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("unsupported " + name + " version: " + ex, ex);
            }
        });
    }

    private void attempt(String name, Runnable action) {
        try {
            action.run();
            active.add(name);
            plugin.log().info("[hooks] Hooked into " + name + ".");
        } catch (RuntimeException | LinkageError ex) {
            plugin.log().warn("[hooks] Could not hook into " + name + " - continuing without it (" + ex.getMessage() + ").");
        }
    }

    /** Re-applies settings that can change on reload (e.g. PAPI parsing in messages). */
    public void reload() {
        if (placeholderApi != null) {
            placeholderApi.applyMessageBridge();
        }
    }

    public void disableAll() {
        if (placeholderApi != null) {
            try {
                placeholderApi.unregister();
            } catch (RuntimeException | LinkageError ignored) {
                // PlaceholderAPI already disabled
            }
            placeholderApi = null;
        }
        if (luckPerms != null) {
            try {
                luckPerms.close();
            } catch (RuntimeException | LinkageError ignored) {
                // LuckPerms already disabled
            }
            luckPerms = null;
        }
        vault = null;
        active.clear();
    }

    public List<String> activeHooks() {
        List<String> list = new ArrayList<>(active);
        list.add("Holograms: " + (plugin.holograms() == null ? "-" : plugin.holograms().name()));
        return list;
    }

    // ------------------------------------------------------------------ holograms

    public HologramProvider createHologramProvider() {
        String mode = plugin.settings().integrations.holograms();
        Plugin decent = Bukkit.getPluginManager().getPlugin("DecentHolograms");
        boolean decentAvailable = decent != null && decent.isEnabled();
        if (("AUTO".equals(mode) || "DECENTHOLOGRAMS".equals(mode)) && decentAvailable) {
            try {
                HologramProvider provider = new DecentHologramsProvider(decent);
                plugin.log().info("[hooks] Using DecentHolograms for revive beacon holograms.");
                return provider;
            } catch (ReflectiveOperationException | LinkageError ex) {
                plugin.log().warn("[hooks] DecentHolograms API not compatible (" + ex + "), using native holograms.");
            }
        } else if ("DECENTHOLOGRAMS".equals(mode)) {
            plugin.log().warn("[hooks] integrations.holograms is DECENTHOLOGRAMS but the plugin is not installed - using native holograms.");
        }
        return new NativeHologramProvider(plugin.itemKeys().hologram);
    }

    // ------------------------------------------------------------------ permissions

    /** Permission check for players who may be offline (uses LuckPerms when available). */
    public boolean hasOfflinePermission(UUID uuid, String permission) {
        OfflinePlayer online = Bukkit.getPlayer(uuid);
        if (online != null && online.isOnline()) {
            return Bukkit.getPlayer(uuid).hasPermission(permission);
        }
        if (luckPerms != null) {
            try {
                return luckPerms.hasPermission(uuid, permission);
            } catch (RuntimeException | LinkageError ex) {
                return false;
            }
        }
        return false;
    }

    public boolean hasLuckPerms() {
        return luckPerms != null;
    }

    // ------------------------------------------------------------------ economy

    public boolean economyAvailable() {
        return vault != null && vault.isAvailable();
    }

    public boolean hasMoney(OfflinePlayer player, double amount) {
        return amount <= 0 || (economyAvailable() && vault.has(player, amount));
    }

    public boolean withdrawMoney(OfflinePlayer player, double amount) {
        return amount <= 0 || (economyAvailable() && vault.withdraw(player, amount));
    }

    public String formatMoney(double amount) {
        return vault != null ? vault.format(amount) : String.format(java.util.Locale.ROOT, "%.2f", amount);
    }
}
