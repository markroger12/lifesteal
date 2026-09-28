package com.example.lifecore.util;

import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Structured logger used across the plugin.
 * <p>
 * Messages are prefixed with a component tag and may carry key/value context, e.g.
 * {@code [death] victim=Steve killer=Alex loss=1.0}. Debug output is only produced when
 * {@code general.debug} is enabled in config.yml.
 */
public final class LifeLogger {

    private final Logger logger;
    private volatile boolean debug;

    public LifeLogger(Logger logger) {
        this.logger = logger;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public boolean isDebug() {
        return debug;
    }

    public void info(String message) {
        logger.info(message);
    }

    public void info(String component, String message, Object... context) {
        logger.info(format(component, message, context));
    }

    public void warn(String message) {
        logger.warning(message);
    }

    public void warn(String component, String message, Object... context) {
        logger.warning(format(component, message, context));
    }

    public void warn(String message, Throwable throwable) {
        logger.log(Level.WARNING, message, throwable);
    }

    public void error(String message, Throwable throwable) {
        logger.log(Level.SEVERE, message, throwable);
    }

    public void error(String message) {
        logger.severe(message);
    }

    public void debug(String component, Supplier<String> message) {
        if (debug) {
            logger.info("[debug] [" + component + "] " + message.get());
        }
    }

    public void debug(String component, String message, Object... context) {
        if (debug) {
            logger.info("[debug] " + format(component, message, context));
        }
    }

    /**
     * Formats a structured log line.
     *
     * @param component the subsystem tag
     * @param message   the human readable message
     * @param context   alternating key/value pairs
     * @return formatted line
     */
    public static String format(String component, String message, Object... context) {
        StringBuilder builder = new StringBuilder(64);
        builder.append('[').append(component).append("] ").append(message);
        if (context != null && context.length > 0) {
            for (int i = 0; i + 1 < context.length; i += 2) {
                builder.append(' ').append(context[i]).append('=').append(context[i + 1]);
            }
            if (context.length % 2 == 1) {
                builder.append(' ').append(context[context.length - 1]);
            }
        }
        return builder.toString();
    }

    public Logger raw() {
        return logger;
    }
}
