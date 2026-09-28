package com.example.lifecore.api;

import java.util.Locale;
import java.util.Optional;

/**
 * Categories of plugin-owned items. Stored in the item's PersistentDataContainer.
 */
public enum LifeCoreItemType {
    HEART,
    SCROLL,
    BEACON,
    NOTE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<LifeCoreItemType> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (LifeCoreItemType type : values()) {
            if (type.id().equalsIgnoreCase(id)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
