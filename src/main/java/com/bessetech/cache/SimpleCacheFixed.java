package com.bessetech.cache;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

public final class SimpleCacheFixed<K, V> implements AutoCloseable {

    private final ConcurrentHashMap<K, CacheEntry<V>> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<K, CompletableFuture<V>> inFlightLoads =
            new ConcurrentHashMap<>();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ScheduledExecutorService cleanupExecutor;

    private final long ttlNanos;
    private final int maxSize;

    public SimpleCacheFixed(Duration ttl, int maxSize) {
        Objects.requireNonNull(ttl, "ttl must not be null");

        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive");
        }

        this.ttlNanos = ttl.toNanos();
        this.maxSize = maxSize;

        ThreadFactory daemonThreadFactory = runnable -> {
            Thread thread = new Thread(runnable, "simple-cache-cleanup");
            thread.setDaemon(true);
            return thread;
        };

        this.cleanupExecutor =
                Executors.newSingleThreadScheduledExecutor(daemonThreadFactory);

        long cleanupIntervalMs = Math.max(1_000L, ttl.toMillis() / 2);

        cleanupExecutor.scheduleAtFixedRate(
                this::removeExpiredEntries,
                cleanupIntervalMs,
                cleanupIntervalMs,
                TimeUnit.MILLISECONDS
        );
    }

    public void put(K key, V value) {
        requireKeyAndValue(key, value);

        lock.writeLock().lock();
        try {
            cache.put(key, new CacheEntry<>(value, System.nanoTime()));
            evictIfOverCapacity();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Uses expire-after-access behavior: successfully reading an entry
     * refreshes its TTL.
     */
    public V get(K key) {
        Objects.requireNonNull(key, "key must not be null");

        lock.writeLock().lock();
        try {
            CacheEntry<V> entry = cache.get(key);

            if (entry == null) {
                return null;
            }

            long now = System.nanoTime();

            if (isExpired(entry, now)) {
                cache.remove(key, entry);
                return null;
            }

            entry.lastAccessNanos = now;
            return entry.value;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Prevents a cache stampede: concurrent callers loading the same missing
     * key share one loader execution.
     */
    public V getOrLoad(K key, Function<? super K, ? extends V> loader) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(loader, "loader must not be null");

        V cachedValue = get(key);
        if (cachedValue != null) {
            return cachedValue;
        }

        CompletableFuture<V> newLoad = new CompletableFuture<>();
        CompletableFuture<V> existingLoad = inFlightLoads.putIfAbsent(key, newLoad);
        CompletableFuture<V> loadToWaitFor =
                existingLoad != null ? existingLoad : newLoad;

        if (existingLoad == null) {
            try {
                V loadedValue = Objects.requireNonNull(
                        loader.apply(key),
                        "loader must not return null"
                );

                put(key, loadedValue);
                newLoad.complete(loadedValue);
            } catch (Throwable error) {
                newLoad.completeExceptionally(error);
            } finally {
                inFlightLoads.remove(key, newLoad);
            }
        }

        try {
            return loadToWaitFor.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Cache load interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Cache load failed", e.getCause());
        }
    }

    /**
     * Returns the current number of non-expired entries.
     */
    public int size() {
        lock.writeLock().lock();
        try {
            removeExpiredEntriesUnderLock();
            return cache.size();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            cache.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void removeExpiredEntries() {
        lock.writeLock().lock();
        try {
            removeExpiredEntriesUnderLock();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void removeExpiredEntriesUnderLock() {
        long now = System.nanoTime();

        for (Map.Entry<K, CacheEntry<V>> mapEntry : cache.entrySet()) {
            CacheEntry<V> entry = mapEntry.getValue();

            if (isExpired(entry, now)) {
                // Conditional removal prevents deleting a replacement entry.
                cache.remove(mapEntry.getKey(), entry);
            }
        }
    }

    private void evictIfOverCapacity() {
        removeExpiredEntriesUnderLock();

        Iterator<Map.Entry<K, CacheEntry<V>>> iterator =
                cache.entrySet().iterator();

        while (cache.size() > maxSize && iterator.hasNext()) {
            Map.Entry<K, CacheEntry<V>> entry = iterator.next();
            cache.remove(entry.getKey(), entry.getValue());
        }
    }

    private boolean isExpired(CacheEntry<V> entry, long nowNanos) {
        return nowNanos - entry.lastAccessNanos >= ttlNanos;
    }

    private void requireKeyAndValue(K key, V value) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
    }

    @Override
    public void close() {
        cleanupExecutor.shutdownNow();
        clear();
    }

    private static final class CacheEntry<V> {
        private final V value;
        private long lastAccessNanos;

        private CacheEntry(V value, long lastAccessNanos) {
            this.value = value;
            this.lastAccessNanos = lastAccessNanos;
        }
    }
}
