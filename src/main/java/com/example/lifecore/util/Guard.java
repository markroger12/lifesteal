package com.example.lifecore.util;

import java.util.function.Supplier;

/**
 * Exception isolation helpers. A failure while handling one player's event must never
 * break processing for other players or leave the plugin in a broken state.
 */
public final class Guard {

    private Guard() {
    }

    public static void run(LifeLogger logger, String context, Runnable action) {
        try {
            action.run();
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable throwable) {
            logger.error("Unhandled exception in " + context + " - the action was aborted safely.", throwable);
        }
    }

    public static <T> T call(LifeLogger logger, String context, Supplier<T> action, T fallback) {
        try {
            return action.get();
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable throwable) {
            logger.error("Unhandled exception in " + context + " - returning fallback value.", throwable);
            return fallback;
        }
    }

    /**
     * Runs a purely cosmetic action (sound, particle, title, ...). Failures - for example an API
     * a platform does not implement - are logged at debug level and never affect gameplay logic.
     */
    public static void cosmetic(LifeLogger logger, String context, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | LinkageError ex) {
            logger.debug("cosmetic", () -> context + " failed: " + ex);
        }
    }

    public static Runnable wrap(LifeLogger logger, String context, Runnable action) {
        return () -> run(logger, context, action);
    }
}
