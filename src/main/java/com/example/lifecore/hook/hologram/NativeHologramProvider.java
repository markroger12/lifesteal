package com.example.lifecore.hook.hologram;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Built-in hologram backend using a single {@link TextDisplay} entity per hologram.
 * The entity is non-persistent, so it is never saved to disk and cannot be left behind by a crash.
 */
public final class NativeHologramProvider implements HologramProvider {

    private final NamespacedKey tagKey;

    public NativeHologramProvider(NamespacedKey tagKey) {
        this.tagKey = tagKey;
    }

    @Override
    public String name() {
        return "native (TextDisplay)";
    }

    @Override
    public Hologram create(String id, Location location, List<String> lines) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Hologram location has no world");
        }
        TextDisplay display = world.spawn(location, TextDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setShadowed(true);
            entity.setSeeThrough(false);
            entity.setDefaultBackground(false);
            entity.setBackgroundColor(Color.fromARGB(96, 0, 0, 0));
            entity.setLineWidth(240);
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setText(String.join("\n", lines));
            entity.getPersistentDataContainer().set(tagKey, PersistentDataType.STRING, id);
        });
        return new NativeHologram(display, location.clone());
    }

    private static final class NativeHologram implements Hologram {

        private final TextDisplay display;
        private final Location location;
        private String lastText = "";

        private NativeHologram(TextDisplay display, Location location) {
            this.display = display;
            this.location = location;
        }

        @Override
        public void setLines(List<String> lines) {
            String text = String.join("\n", lines);
            if (!text.equals(lastText)) {
                display.setText(text);
                lastText = text;
            }
        }

        @Override
        public boolean isValid() {
            return display.isValid();
        }

        @Override
        public Location location() {
            return location.clone();
        }

        @Override
        public void remove() {
            if (display.isValid()) {
                display.remove();
            }
        }
    }
}
