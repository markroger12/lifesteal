package com.example.lifecore.item;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * PersistentDataContainer keys used on LifeCore items, holograms and blocks.
 */
public final class ItemKeys {

    public final NamespacedKey type;
    public final NamespacedKey id;
    public final NamespacedKey value;
    public final NamespacedKey uniqueId;
    public final NamespacedKey owner;
    public final NamespacedKey created;
    public final NamespacedKey signature;
    public final NamespacedKey version;
    public final NamespacedKey hologram;

    public ItemKeys(Plugin plugin) {
        this.type = new NamespacedKey(plugin, "type");
        this.id = new NamespacedKey(plugin, "id");
        this.value = new NamespacedKey(plugin, "value");
        this.uniqueId = new NamespacedKey(plugin, "uid");
        this.owner = new NamespacedKey(plugin, "owner");
        this.created = new NamespacedKey(plugin, "created");
        this.signature = new NamespacedKey(plugin, "sig");
        this.version = new NamespacedKey(plugin, "version");
        this.hologram = new NamespacedKey(plugin, "hologram");
    }
}
