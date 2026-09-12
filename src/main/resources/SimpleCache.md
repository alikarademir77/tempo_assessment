## Code Review

You are reviewing the following code submitted as part of a task to implement an item cache in a highly concurrent application. The anticipated load includes: thousands of reads per second, hundreds of writes per second, tens of concurrent threads.
Your objective is to identify and explain the issues in the implementation that must be addressed before deploying the code to production. Please provide a clear explanation of each issue and its potential impact on production behaviour.

```kotlin
import java.util.concurrent.ConcurrentHashMap

class SimpleCache<K, V> {
    private val cache = ConcurrentHashMap<K, CacheEntry<V>>()
    private val ttlMs = 60000 // 1 minute
    
    data class CacheEntry<V>(val value: V, val timestamp: Long)
    
    fun put(key: K, value: V) {
        cache[key] = CacheEntry(value, System.currentTimeMillis())
    }
    
    fun get(key: K): V? {
        val entry = cache[key]
        if (entry != null) {
            if (System.currentTimeMillis() - entry.timestamp < ttlMs) {
                return entry.value
            }
        }
        return null
    }
    
    fun size(): Int {
        return cache.size
    }
}
```
REVIEW RESULTS:
I'm not familiar with Kotlin at the moment, so review is done based on java version. 
Java version of same code with some review comments embedded as java comments is provided in 
reviewed package.  

Below is complete list of review items. 

A new version of Cache that have all identified issues being 
fixed is provided under fixed package.

1. Expired entries remain in the ConcurrentHashMap indefinitely. With
continuing writes, memory * usage and reported size grow without bound.

2. size() is not a count of valid entries. It includes expired entries, so it
can be misleading * for monitoring, capacity limits, or cache metrics.

3. Expiration check and removal would need to be conditional. If you simply
add cache.remove(key) on expiry, a concurrent put() could replace the entry
between the read and remove—then the read thread might delete the new value.
Use cache.remove(key, entry) instead.

4. A read can return a stale value during a concurrent write. A writer may 
update the same key immediately after the reader fetches the old entry. The
reader can still return that old value. This is thread-safe, but not
“latest-write-wins” at the exact instant of the read.

5. Clock-based TTL is vulnerable to system-clock changes. NTP/manual clock
adjustments can make entries expire too early, too late, or appear valid
again after expiry. TTL duration measurement should use monotonic time, e.g.
System.nanoTime().

6. TTL is hard-coded and uses an implicit type conversion. 60000 is an int
literal widened to long. It works, but 60_000L is clearer. More importantly,
a fixed TTL makes testing and configuration harder.

7. Null keys/values are not supported. ConcurrentHashMap rejects null keys
and values with NullPointerException. The API does not communicate this
constraint. This can surface under production input edge cases.

8. No bounded capacity or eviction strategy. Even with expiry-on-read, keys
that are never read again remain. Under a high write rate, the cache can
consume excessive memory unless it has periodic cleanup, maximum size, or
another eviction policy.

9. Cleanup performed only by reads can create write-heavy memory growth. With
hundreds of writes per second and keys that are rarely re-read, expired
records accumulate. A background cleanup process or bounded cache policy may
be required.

10. ConcurrentHashMap.size() is weakly consistent during mutations. It may
not reflect the most recent updates, and it may not be an accurate count of
entries. This can lead to misleading capacity checks.

11. Timestamp represents creation time only. This is an expire-after-write
cache. If the intended behaviour is expire-after-access, frequently used
entries will still expire after one minute.

12. Potential cache stampede after expiry. If many threads request the same
expired key, all can miss at once and independently reload the same expensive
data from a database or remote service. This is especially costly with
thousands of reads per second.
