package com.example.lifecore.hook.hologram;

import org.bukkit.Location;

import java.util.List;

/**
 * A multi-line hologram. All methods must be called on the thread owning its location.
 */
public interface Hologram {

    void setLines(List<String> lines);

    boolean isValid();

    Location location();

    void remove();
}
