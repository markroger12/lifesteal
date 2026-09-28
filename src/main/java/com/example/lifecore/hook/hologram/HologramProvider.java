package com.example.lifecore.hook.hologram;

import org.bukkit.Location;

import java.util.List;

/**
 * Creates holograms. Implementations exist for native display entities and DecentHolograms.
 */
public interface HologramProvider {

    String name();

    /** Creates a hologram. Must be called on the thread owning the location. */
    Hologram create(String id, Location location, List<String> lines);

    default void shutdown() {
    }
}
