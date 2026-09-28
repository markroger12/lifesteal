package com.example.lifecore.manager;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CooldownManagerTest {

    @Test
    void cooldownsExpire() {
        AtomicLong clock = new AtomicLong(0);
        CooldownManager cooldowns = new CooldownManager(clock::get);
        UUID player = UUID.randomUUID();
        assertTrue(cooldowns.tryAcquire("withdraw", player, null, 1000));
        assertFalse(cooldowns.tryAcquire("withdraw", player, null, 1000));
        assertEquals(1000, cooldowns.remaining("withdraw", player));
        clock.set(1000);
        assertEquals(0, cooldowns.remaining("withdraw", player));
        assertTrue(cooldowns.tryAcquire("withdraw", player, null, 1000));
    }

    @Test
    void subKeysAreIndependent() {
        CooldownManager cooldowns = new CooldownManager(() -> 0L);
        UUID player = UUID.randomUUID();
        cooldowns.set("heart-item", player, "small_heart", 5000);
        assertEquals(5000, cooldowns.remaining("heart-item", player, "small_heart"));
        assertEquals(0, cooldowns.remaining("heart-item", player, "large_heart"));
    }
}
