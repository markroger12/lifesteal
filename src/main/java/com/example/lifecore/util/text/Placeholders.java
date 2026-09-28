package com.example.lifecore.util.text;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Simple {@code {key}} placeholder container used by messages, menus, holograms and items.
 * Values are converted with {@link String#valueOf(Object)} when added.
 */
public final class Placeholders implements UnaryOperator<String> {

    public static final Placeholders EMPTY = new Placeholders(Map.of());

    private final Map<String, String> values;

    public Placeholders() {
        this.values = new LinkedHashMap<>();
    }

    private Placeholders(Map<String, String> values) {
        this.values = values;
    }

    public static Placeholders of(String key, Object value) {
        return new Placeholders().add(key, value);
    }

    public static Placeholders of(String k1, Object v1, String k2, Object v2) {
        return new Placeholders().add(k1, v1).add(k2, v2);
    }

    public static Placeholders of(String k1, Object v1, String k2, Object v2, String k3, Object v3) {
        return new Placeholders().add(k1, v1).add(k2, v2).add(k3, v3);
    }

    public Placeholders add(String key, Object value) {
        values.put(key, value == null ? "" : String.valueOf(value));
        return this;
    }

    public Placeholders addAll(Placeholders other) {
        if (other != null) {
            for (Map.Entry<String, String> entry : other.values.entrySet()) {
                add(entry.getKey(), entry.getValue());
            }
        }
        return this;
    }

    public Placeholders copy() {
        return new Placeholders().addAll(this);
    }

    public String get(String key) {
        return values.get(key);
    }

    @Override
    public String apply(String input) {
        if (input == null || input.isEmpty() || values.isEmpty() || input.indexOf('{') < 0) {
            return input == null ? "" : input;
        }
        StringBuilder out = new StringBuilder(input.length() + 32);
        int length = input.length();
        int i = 0;
        while (i < length) {
            char c = input.charAt(i);
            if (c == '{') {
                int end = input.indexOf('}', i + 1);
                if (end > i + 1) {
                    String key = input.substring(i + 1, end);
                    String replacement = values.get(key);
                    if (replacement != null) {
                        out.append(replacement);
                        i = end + 1;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    public List<String> apply(List<String> input) {
        return input.stream().map(this).toList();
    }
}
