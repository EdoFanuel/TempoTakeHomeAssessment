import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class SimpleCache<K, V> (
    val ttlMs: Int = 60000, // 1 minute
    val capacity: Int = Int.MAX_VALUE,
    val offset: Double = 0.0
) {
    private val cache = ConcurrentHashMap<K, CacheEntry<V>>()
    private val queue = ArrayDeque<K>()

    data class CacheEntry<V>(val value: V, val timestamp: Long)

    fun put(key: K, value: V) {
        val minTtl = ttlMs * (1 - offset)
        val maxTtl = ttlMs * (1 + offset)
        val ttl = Random.nextDouble(minTtl, maxTtl).toLong()
        cache[key] = CacheEntry(value, System.currentTimeMillis() + ttl)
        queue.addFirst(key)
        evict()
    }

    fun get(key: K): V? {
        val entry = cache[key] ?: return null
        // Key is accessed. Move it to the most recently used
        queue.remove(key)
        queue.addFirst(key)
        if (entry.timestamp >= System.currentTimeMillis()) return entry.value
        // Entry has expired
        cache.remove(key)
        queue.remove(key)
        return null
    }

    fun size(): Int = cache.size

    fun evict() {
        if (size() <= capacity) return
        val leastRecentlyUsed = queue.removeLast()
        cache.remove(leastRecentlyUsed)
    }
}