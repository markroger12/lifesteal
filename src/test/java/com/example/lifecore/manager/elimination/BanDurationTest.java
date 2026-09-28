package com.example.lifecore.manager.elimination;

import com.example.lifecore.TestResources;
import com.example.lifecore.configuration.settings.LifeCoreSettings;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BanDurationTest {

    private static final long HOUR = 3_600_000L;

    @Test
    void defaultDurationApplies() {
        LifeCoreSettings.Ban ban = TestResources.defaultSettings().elimination.ban();
        assertEquals(24 * HOUR, EliminationManager.resolveBanDuration(ban, permission -> false));
    }

    @Test
    void shortestPermissionDurationWins() {
        LifeCoreSettings.Ban ban = TestResources.defaultSettings().elimination.ban();
        assertEquals(12 * HOUR, EliminationManager.resolveBanDuration(ban, Set.of("lifecore.ban.vip")::contains));
        assertEquals(6 * HOUR, EliminationManager.resolveBanDuration(ban, Set.of("lifecore.ban.vip", "lifecore.ban.mvp")::contains));
    }

    @Test
    void disabledBansReturnZero() {
        LifeCoreSettings.Ban ban = TestResources.settings("elimination:\n  ban:\n    enabled: false").elimination.ban();
        assertEquals(0, EliminationManager.resolveBanDuration(ban, permission -> true));
    }

    @Test
    void permanentBansRequireExplicitOptIn() {
        LifeCoreSettings.Ban notAllowed = TestResources.settings("elimination:\n  ban:\n    durations:\n      default: permanent").elimination.ban();
        assertEquals(24 * HOUR, EliminationManager.resolveBanDuration(notAllowed, permission -> false),
                "permanent must fall back to the safe default when allow-permanent is false");

        LifeCoreSettings.Ban allowed = TestResources.settings(
                "elimination:\n  ban:\n    allow-permanent: true\n    durations:\n      default: permanent").elimination.ban();
        assertEquals(-1, EliminationManager.resolveBanDuration(allowed, permission -> false));
        assertEquals(12 * HOUR, EliminationManager.resolveBanDuration(allowed, Set.of("lifecore.ban.vip")::contains),
                "a finite tier is shorter than a permanent default");
    }
}
