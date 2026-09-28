package com.example.lifecore.configuration;

import com.example.lifecore.util.TimeUtil;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Typed, validating configuration reader.
 * <p>
 * Invalid values never throw: they are replaced by the default (or clamped into range) and a
 * human-readable problem is recorded so administrators see exactly what was wrong.
 */
public final class ConfigReader {

    private final ConfigurationSection root;
    private final String fileName;
    private final List<String> problems;
    private final Set<String> accessed;

    public ConfigReader(ConfigurationSection root, String fileName) {
        this(root, fileName, new ArrayList<>(), new LinkedHashSet<>());
    }

    private ConfigReader(ConfigurationSection root, String fileName, List<String> problems, Set<String> accessed) {
        this.root = root;
        this.fileName = fileName;
        this.problems = problems;
        this.accessed = accessed;
    }

    private void track(String path) {
        String base = root.getCurrentPath();
        accessed.add(base == null || base.isEmpty() ? path : base + "." + path);
    }

    /** @return every full path that was read through this reader (used by tests to verify defaults). */
    public Set<String> accessedPaths() {
        return Collections.unmodifiableSet(accessed);
    }

    /** @return a reader for a child section sharing the same problem list (empty section if missing). */
    public ConfigReader child(String path) {
        ConfigurationSection section = root.getConfigurationSection(path);
        if (section == null) {
            section = root.createSection(path);
        }
        return new ConfigReader(section, fileName, problems, accessed);
    }

    public ConfigurationSection section() {
        return root;
    }

    public boolean has(String path) {
        return root.contains(path);
    }

    public List<String> keys(String path) {
        ConfigurationSection section = path == null || path.isEmpty() ? root : root.getConfigurationSection(path);
        if (section == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(section.getKeys(false));
    }

    public boolean getBoolean(String path, boolean def) {
        track(path);
        Object value = root.get(path);
        if (value == null) {
            return def;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if (text.equals("true") || text.equals("yes") || text.equals("on")) {
            return true;
        }
        if (text.equals("false") || text.equals("no") || text.equals("off")) {
            return false;
        }
        problem(path, "expected true/false but found '" + value + "', using " + def);
        return def;
    }

    public double getDouble(String path, double def, double min, double max) {
        track(path);
        Object value = root.get(path);
        if (value == null) {
            return def;
        }
        double parsed;
        if (value instanceof Number number) {
            parsed = number.doubleValue();
        } else {
            try {
                parsed = Double.parseDouble(String.valueOf(value).trim());
            } catch (NumberFormatException ex) {
                problem(path, "expected a number but found '" + value + "', using " + def);
                return def;
            }
        }
        if (!Double.isFinite(parsed)) {
            problem(path, "NaN/Infinity is not allowed, using " + def);
            return def;
        }
        if (parsed < min) {
            problem(path, parsed + " is below the minimum " + min + ", using " + min);
            return min;
        }
        if (parsed > max) {
            problem(path, parsed + " is above the maximum " + max + ", using " + max);
            return max;
        }
        return parsed;
    }

    public int getInt(String path, int def, int min, int max) {
        return (int) Math.round(getDouble(path, def, min, max));
    }

    /** Reads a duration such as "24h" and returns milliseconds. */
    public long getDuration(String path, String def, boolean allowPermanent) {
        track(path);
        Object raw = root.get(path);
        String text = raw == null ? def : String.valueOf(raw);
        OptionalLong parsed = TimeUtil.parseDuration(text);
        if (parsed.isEmpty()) {
            problem(path, "invalid duration '" + text + "', using " + def);
            parsed = TimeUtil.parseDuration(def);
        }
        long value = parsed.orElse(0L);
        if (value == TimeUtil.PERMANENT && !allowPermanent) {
            problem(path, "permanent durations are not allowed here, using " + def);
            OptionalLong fallback = TimeUtil.parseDuration(def);
            value = fallback.isPresent() && fallback.getAsLong() >= 0 ? fallback.getAsLong() : 0L;
        }
        return value;
    }

    /** Reads seconds as a plain number or a duration string, returning milliseconds. */
    public long getSecondsAsMillis(String path, long defSeconds) {
        track(path);
        Object raw = root.get(path);
        if (raw == null) {
            return defSeconds * 1000L;
        }
        if (raw instanceof Number number) {
            double seconds = number.doubleValue();
            if (!Double.isFinite(seconds) || seconds < 0) {
                problem(path, "must be a positive number of seconds, using " + defSeconds);
                return defSeconds * 1000L;
            }
            return (long) (seconds * 1000L);
        }
        return getDuration(path, defSeconds + "s", false);
    }

    public String getString(String path, String def) {
        track(path);
        Object value = root.get(path);
        if (value == null) {
            return def;
        }
        if (value instanceof ConfigurationSection || value instanceof List<?>) {
            problem(path, "expected text, using default");
            return def;
        }
        return String.valueOf(value);
    }

    public List<String> getStringList(String path) {
        track(path);
        if (!root.contains(path)) {
            return new ArrayList<>();
        }
        Object value = root.get(path);
        if (value instanceof List<?>) {
            return new ArrayList<>(root.getStringList(path));
        }
        if (value instanceof String s) {
            List<String> single = new ArrayList<>();
            single.add(s);
            return single;
        }
        problem(path, "expected a list, using an empty list");
        return new ArrayList<>();
    }

    public <E extends Enum<E>> E getEnum(String path, Class<E> type, E def) {
        String value = getString(path, def.name());
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            StringBuilder allowed = new StringBuilder();
            for (E constant : type.getEnumConstants()) {
                if (allowed.length() > 0) {
                    allowed.append(", ");
                }
                allowed.append(constant.name());
            }
            problem(path, "unknown value '" + value + "' (allowed: " + allowed + "), using " + def.name());
            return def;
        }
    }

    public void problem(String path, String message) {
        String fullPath = root.getCurrentPath() == null || root.getCurrentPath().isEmpty() ? path : root.getCurrentPath() + "." + path;
        problems.add(fileName + " -> " + fullPath + ": " + message);
    }

    public List<String> problems() {
        return problems;
    }
}
