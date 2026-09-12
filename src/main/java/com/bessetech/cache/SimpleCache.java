package com.bessetech.cache;

import java.util.concurrent.ConcurrentHashMap;

public class SimpleCache<K, V> {

    //No bounded capacity or eviction strategy. Even with expiry-on-read, keys that are never read again remain. Under a high write rate, the cache can consume excessive memory unless it has periodic cleanup, maximum size, or another eviction policy.
    private final ConcurrentHashMap<K, CacheEntry<V>> cache = new ConcurrentHashMap<>();
    //TTL is hard-coded and uses an implicit type conversion. 60000 is an int literal widened to long. It works, but 60_000L is clearer. More importantly, a fixed TTL makes testing and configuration harder.
    private final long ttlMs = 60000; // 1 minute

    private static class CacheEntry<V> {

        private final V value;
        private final long timestamp;

        private CacheEntry(V value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }
    }

    public void put(K key, V value) {
        //Null keys/values are not supported. ConcurrentHashMap rejects null keys and values with NullPointerException. The API does not communicate this constraint. This can surface under production input edge cases.

        //Timestamp represents creation time only. This is an expire-after-write cache. If the intended behaviour is expire-after-access, frequently used  entries will still expire after one minute.
        cache.put(key, new CacheEntry<>(value, System.currentTimeMillis()));
    }

    public V get(K key) {
        CacheEntry<V> entry = cache.get(key);

        //Clock-based TTL is vulnerable to system-clock changes.NTP/manual clock adjustments can make entries expire too early, too late, or appear valid again after expiry. TTL duration measurement should use monotonic time, e.g. System.nanoTime()
        if (entry != null
                && System.currentTimeMillis() - entry.timestamp < ttlMs) {
            return entry.value;
            //A read can return a stale value during a concurrent write. A writer may update the same key immediately after the reader fetches the old entry. The reader can still return that old value. This is thread-safe, but not “latest-write-wins” at the exact instant of the read.
        }
        //Expired entries remain in the ConcurrentHashMap indefinitely. With continuing writes,
        //memory usage and reported size grow without bound. Also, due to concurrency, the removed entry should be spcified in the remove() call to avoid removing a new entry that was added after the get() call.
        return null;
    }

    public int size() {
        //size() is not a count of valid entries. It includes expired entries.
        return cache.size();
    }
}