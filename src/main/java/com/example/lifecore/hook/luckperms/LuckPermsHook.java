package com.example.lifecore.hook.luckperms;

import com.example.lifecore.LifeCorePlugin;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * LuckPerms integration.
 * <ul>
 *     <li>Permission checks for offline players (ban durations, caps) using LuckPerms' cache.</li>
 *     <li>Live refresh of heart caps when a player's permissions change.</li>
 * </ul>
 * This class is only loaded when LuckPerms is installed.
 */
public final class LuckPermsHook {

    private final LifeCorePlugin plugin;
    private final LuckPerms api;
    private final EventSubscription<UserDataRecalculateEvent> subscription;

    public LuckPermsHook(LifeCorePlugin plugin) {
        this.plugin = plugin;
        this.api = LuckPermsProvider.get();
        this.subscription = api.getEventBus().subscribe(plugin, UserDataRecalculateEvent.class, this::onRecalculate);
    }

    private void onRecalculate(UserDataRecalculateEvent event) {
        UUID uuid = event.getUser().getUniqueId();
        Player player = Bukkit.getPlayer(uuid);
        plugin.caps().invalidate(uuid);
        if (player != null) {
            plugin.scheduler().runAtEntity(player, () -> {
                if (plugin.settings().hearts.enforceCapOnJoin()) {
                    plugin.hearts().enforceCap(player);
                } else {
                    plugin.hearts().applyAttribute(player);
                }
            });
        }
    }

    /**
     * Checks a permission for a (possibly offline) player without blocking.
     * Returns false when LuckPerms has not loaded the user.
     */
    public boolean hasPermission(UUID uuid, String permission) {
        User user = api.getUserManager().getUser(uuid);
        if (user == null) {
            return false;
        }
        return user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
    }

    public void close() {
        subscription.close();
    }
}
