package com.example.lifecore.manager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * In-memory cooldowns keyed by category + player (+ optional sub key).
 */
public final class CooldownManager {

    private final Map<String, Long> expiries = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public CooldownManager() {
        this(System::currentTimeMillis);
    }

    public CooldownManager(LongSupplier clock) {
        this.clock = clock;
    }

    private static String key(String category, UUID player, String sub) {
        return sub == null ? category + ':' + player : category + ':' + player + ':' + sub;
    }

    public void set(String category, UUID player, long millis) {
        set(category, player, null, millis);
    }

    public void set(String category, UUID player, String sub, long millis) {
        if (millis <= 0) {
            return;
        }
        expiries.put(key(category, player, sub), clock.getAsLong() + millis);
    }

    public long remaining(String category, UUID player) {
        return remaining(category, player, null);
    }

    public long remaining(String category, UUID player, String sub) {
        String key = key(category, player, sub);
        Long expiry = expiries.get(key);
        if (expiry == null) {
            return 0;
        }
        long left = expiry - clock.getAsLong();
        if (left <= 0) {
            expiries.remove(key, expiry);
            return 0;
        }
        return left;
    }

    /** Atomically checks and starts a cooldown. @return true if the action may proceed. */
    public boolean tryAcquire(String category, UUID player, String sub, long millis) {
        if (millis <= 0) {
            return true;
        }
        String key = key(category, player, sub);
        long now = clock.getAsLong();
        boolean[] acquired = {false};
        expiries.compute(key, (k, expiry) -> {
            if (expiry == null || expiry <= now) {
                acquired[0] = true;
                return now + millis;
            }
            return expiry;
        });
        return acquired[0];
    }

    public void clear(String category, UUID player) {
        clear(category, player, null);
    }

    public void clear(String category, UUID player, String sub) {
        expiries.remove(key(category, player, sub));
    }

    /** Removes expired entries (called periodically). */
    public void purge() {
        long now = clock.getAsLong();
        expiries.values().removeIf(expiry -> expiry <= now);
    }
}
