package com.example.lifecore.database;

import com.example.lifecore.storage.StorageException;
import com.example.lifecore.storage.StorageProvider;
import com.example.lifecore.util.LifeLogger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Serialises every database operation onto one dedicated thread.
 * <p>
 * A single thread guarantees ordering (a save queued before a load is always visible to that
 * load), matches SQLite's single-writer model and keeps blocking JDBC work off server threads.
 */
public final class Database {

    @FunctionalInterface
    public interface StorageCall<T> {
        T call(StorageProvider storage) throws StorageException;
    }

    @FunctionalInterface
    public interface StorageAction {
        void run(StorageProvider storage) throws StorageException;
    }

    private final StorageProvider storage;
    private final LifeLogger logger;
    private final ExecutorService executor;
    private final AtomicLong lastFailureLog = new AtomicLong();
    private volatile boolean healthy = true;

    public Database(StorageProvider storage, LifeLogger logger) {
        this.storage = storage;
        this.logger = logger;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "LifeCore-Database");
            thread.setDaemon(false);
            return thread;
        });
    }

    public StorageProvider storage() {
        return storage;
    }

    public boolean isHealthy() {
        return healthy;
    }

    public <T> CompletableFuture<T> submit(String description, StorageCall<T> call) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    T result = call.call(storage);
                    markHealthy();
                    future.complete(result);
                } catch (StorageException ex) {
                    markFailure(description, ex);
                    future.completeExceptionally(ex);
                } catch (Throwable throwable) {
                    logger.error("[storage] Unexpected error during " + description, throwable);
                    future.completeExceptionally(throwable);
                }
            });
        } catch (RejectedExecutionException ex) {
            future.completeExceptionally(new StorageException("Database executor is shut down (" + description + ")", ex));
        }
        return future;
    }

    public CompletableFuture<Void> execute(String description, StorageAction action) {
        return submit(description, s -> {
            action.run(s);
            return null;
        });
    }

    /** Runs a call on the calling thread. Only used during startup/shutdown when no server thread is blocked. */
    public <T> T callDirect(StorageCall<T> call) throws StorageException {
        return call.call(storage);
    }

    private void markHealthy() {
        if (!healthy) {
            healthy = true;
            logger.info("[storage] Database connection restored.");
        }
    }

    private void markFailure(String description, StorageException ex) {
        healthy = storage.ping();
        long now = System.currentTimeMillis();
        long last = lastFailureLog.get();
        if (now - last > 10_000 && lastFailureLog.compareAndSet(last, now)) {
            logger.warn("[storage] " + description + " failed: " + ex.getMessage()
                    + (healthy ? "" : " (database unreachable - changes are kept in memory and retried)"));
        } else {
            logger.debug("storage", () -> description + " failed: " + ex.getMessage());
        }
    }

    /**
     * Stops accepting work and waits for queued operations (including final saves) to finish.
     */
    public void shutdown(long timeoutSeconds) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                logger.warn("[storage] Timed out waiting for pending database operations; forcing shutdown.");
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        storage.close();
    }
}
