package com.example.lifecore.item;

import com.example.lifecore.api.LifeCoreItemType;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Data read back from a LifeCore item's PersistentDataContainer.
 *
 * @param type      item category (null if the stored type is unknown)
 * @param id        definition id (heart/scroll id, beacon tier, "note")
 * @param value     stored value (heart note hearts)
 * @param uniqueId  unique id for non-stackable items (notes, beacons)
 * @param owner     creator of the item (notes)
 * @param created   creation timestamp
 * @param valid     true if all required fields exist and the signature matches
 */
public record ItemIdentity(@Nullable LifeCoreItemType type, String id, double value, @Nullable UUID uniqueId,
                           @Nullable UUID owner, long created, boolean valid) {
}
