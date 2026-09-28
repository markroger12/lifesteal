package com.example.lifecore.storage;

/**
 * Result of atomically consuming a unique item id (heart notes, beacons).
 */
public enum ConsumeResult {
    /** The id was unused and is now marked consumed. */
    CONSUMED,
    /** The id was already consumed - the item is a duplicate. */
    ALREADY_CONSUMED
}
