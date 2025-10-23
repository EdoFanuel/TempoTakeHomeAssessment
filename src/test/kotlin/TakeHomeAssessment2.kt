import java.util.concurrent.ConcurrentHashMap

class SimpleCache<K, V> (val ttlMs: Int, val capacity: Int) {
    private val cache = ConcurrentHashMap<K, CacheEntry<V>>()
    private val queue = ArrayDeque<K>()

    data class CacheEntry<V>(val value: V, val timestamp: Long)

    fun put(key: K, value: V) {
        cache[key] = CacheEntry(value, System.currentTimeMillis())
        queue.addFirst(key)
        evict()
    }

    fun get(key: K): V? {
        val entry = cache[key]
        if (entry != null) {
            // Key is accessed. Move it to the most recently used
            queue.remove(key)
            queue.addFirst(key)
            if (System.currentTimeMillis() - entry.timestamp < ttlMs) {
                return entry.value
            } else {
                cache.remove(key)
            }
        }
        return null
    }

    fun size(): Int {
        return cache.size
    }

    fun evict() {
        if (size() > capacity) {
            val leastRecentlyUsed = queue.removeLast()
            cache.remove(leastRecentlyUsed)
        }
    }
}