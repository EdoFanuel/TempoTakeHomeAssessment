## Code Review

You are reviewing the following code submitted as part of a task to implement an item cache in a highly concurrent application. The anticipated load includes: thousands of reads per second, hundreds of writes per second, tens of concurrent threads.
Your objective is to identify and explain the issues in the implementation that must be addressed before deploying the code to production. Please provide a clear explanation of each issue and its potential impact on production behaviour.

```kotlin
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

# My Feedbacks
1. TTL should not be hardcoded. Move this to a external configuration and use dependency injection.
   1. This will make it near impossible to change in production without building & deploying a new code.
   2. Having it hardcoded will also make it impossible to have different values between environment. For example: 
      1. Testing environment might want a lower TTL to test the expiration logic faster
      2. Production environment however might want to use longer TTL to avoid fetching the value from DB too often.
   3. This is also implemented with generic types. Which indicates this will be used to cache different types of data with different expiry duration
   4. Here's a quick fix:
    ```kotlin
   // Notice the ttlMs is now a dependency injected through the constructor 
   class SimpleCache<K, V> (val ttlMs: Int) {
        private val cache = ConcurrentHashMap<K, CacheEntry<V>>()
        // Rest of the code
    }
    ```
2. Need to remove the entry from the map if it's already expired. Otherwise, it will live in the memory forever. 
   1. Here's one way to fix it. 
    ```kotlin
    fun get(key: K): V? {
        val entry = cache[key]
        if (entry != null) {
            if (System.currentTimeMillis() - entry.timestamp < ttlMs) {
                return entry.value
            } else {
                cache.remove(key) // Add this part to remove the expired entry
            }
        }
        return null
    }
    ```
   2. Alternatively, we can also run a periodic scheduler that will go through all entries and remove any expired ones.  
3. Unless we know how many unique keys are possible for this cache, we will need to set maximum capacity.
   1. Without capacity, we cannot control how much memory every cache will take. Leading to a risk of `OutOfMemoryError`. 
   2. Here's a rough way to implement capacity and make sure we're not exceeding the capacity using queue.
    ```kotlin
   // Notice that we're adding the capacity through dependency injection as well
    class SimpleCache<K, V> (val ttlMs: Int, val capacity: Int) {
        private val cache = ConcurrentHashMap<K, CacheEntry<V>>()
        private val queue = ArrayDeque<K>()
    
        data class CacheEntry<V>(val value: V, val timestamp: Long)
    
        fun put(key: K, value: V) {
            cache[key] = CacheEntry(value, System.currentTimeMillis())
            // New entry is always considered most recently used
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
    ```
   3. Example above is an LRU cache where least recently used entry are removed when capacity is reached. There's also LFU where we remove least frequently used instead.