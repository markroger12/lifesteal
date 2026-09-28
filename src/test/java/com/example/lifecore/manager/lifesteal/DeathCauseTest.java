package com.example.lifecore.manager.lifesteal;

import com.example.lifecore.TestResources;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DeathCauseTest {

    @Test
    void mapsDamageCauses() {
        assertEquals("LAVA", DeathCause.fromDamageCause("LAVA").primary());
        assertEquals("FIRE", DeathCause.fromDamageCause("FIRE_TICK").primary());
        assertEquals("EXPLOSION", DeathCause.fromDamageCause("BLOCK_EXPLOSION").primary());
        assertEquals("EXPLOSION", DeathCause.fromDamageCause("ENTITY_EXPLOSION").primary());
        assertEquals("VOID", DeathCause.fromDamageCause("VOID").primary());
        assertEquals("WITHER_EFFECT", DeathCause.fromDamageCause("WITHER").primary());
        assertEquals("KILL_COMMAND", DeathCause.fromDamageCause("KILL").primary());
        assertEquals("OTHER", DeathCause.fromDamageCause("SOMETHING_NEW").primary());
        assertFalse(DeathCause.fromDamageCause("FALL").mob());
    }

    @Test
    void configuredLossesPerCause() {
        var death = TestResources.defaultSettings().death;
        assertEquals(2.0, death.lossFor("LAVA"));
        assertEquals(3.0, death.lossFor("GHAST"));
        assertEquals(0.0, death.lossFor("KILL_COMMAND"));
        assertEquals(1.0, death.lossFor("FALL"));
    }

    @Test
    void mobCausesFallBackToEntity() {
        DeathCause zombie = new DeathCause("ZOMBIE", List.of("ZOMBIE", "ENTITY"), true);
        var death = TestResources.defaultSettings().death;
        Double loss = null;
        for (String key : zombie.lookupChain()) {
            loss = death.lossFor(key);
            if (loss != null) {
                break;
            }
        }
        assertEquals(1.0, loss);
    }

    @Test
    void disabledCausesLoseNothing() {
        var death = TestResources.settings("death:\n  disabled-causes: [LAVA]").death;
        assertEquals(0.0, death.lossFor("LAVA"));
    }
}
