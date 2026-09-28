package com.example.lifecore.util.scheduler;

/**
 * Platform independent handle for a scheduled task.
 */
public interface ScheduledTask {

    void cancel();

    boolean isCancelled();

    ScheduledTask NOOP = new ScheduledTask() {
        @Override
        public void cancel() {
        }

        @Override
        public boolean isCancelled() {
            return true;
        }
    };
}
