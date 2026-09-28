package com.example.lifecore.hook.hologram;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * DecentHolograms backend, bound via reflection so LifeCore has no compile-time dependency on it.
 * Holograms are created with saveToFile=false so they never persist outside LifeCore's control.
 */
public final class DecentHologramsProvider implements HologramProvider {

    private final Method createHologram;
    private final Method setHologramLines;
    private final Method removeHologram;
    private final Method getHologram;

    public DecentHologramsProvider(Plugin decentHolograms) throws ReflectiveOperationException {
        ClassLoader loader = decentHolograms.getClass().getClassLoader();
        Class<?> api = Class.forName("eu.decentsoftware.holograms.api.DHAPI", true, loader);
        Class<?> hologramClass = Class.forName("eu.decentsoftware.holograms.api.holograms.Hologram", true, loader);
        this.createHologram = api.getMethod("createHologram", String.class, Location.class, boolean.class, List.class);
        this.setHologramLines = api.getMethod("setHologramLines", hologramClass, List.class);
        this.removeHologram = api.getMethod("removeHologram", String.class);
        this.getHologram = api.getMethod("getHologram", String.class);
    }

    @Override
    public String name() {
        return "DecentHolograms";
    }

    @Override
    public Hologram create(String id, Location location, List<String> lines) {
        String name = "lifecore_" + id.replaceAll("[^A-Za-z0-9_-]", "");
        try {
            Object existing = getHologram.invoke(null, name);
            if (existing != null) {
                removeHologram.invoke(null, name);
            }
            Object hologram = createHologram.invoke(null, name, location, false, new ArrayList<>(lines));
            return new DecentHologram(name, hologram, location.clone());
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("DecentHolograms failed to create hologram " + name, ex);
        }
    }

    private final class DecentHologram implements Hologram {

        private final String name;
        private final Object handle;
        private final Location location;
        private boolean removed;
        private List<String> last = List.of();

        private DecentHologram(String name, Object handle, Location location) {
            this.name = name;
            this.handle = handle;
            this.location = location;
        }

        @Override
        public void setLines(List<String> lines) {
            if (removed || lines.equals(last)) {
                return;
            }
            try {
                setHologramLines.invoke(null, handle, new ArrayList<>(lines));
                last = List.copyOf(lines);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("DecentHolograms failed to update hologram " + name, ex);
            }
        }

        @Override
        public boolean isValid() {
            if (removed) {
                return false;
            }
            try {
                return getHologram.invoke(null, name) != null;
            } catch (ReflectiveOperationException ex) {
                return false;
            }
        }

        @Override
        public Location location() {
            return location.clone();
        }

        @Override
        public void remove() {
            if (removed) {
                return;
            }
            removed = true;
            try {
                removeHologram.invoke(null, name);
            } catch (ReflectiveOperationException ignored) {
                // DecentHolograms already disabled
            }
        }
    }
}
