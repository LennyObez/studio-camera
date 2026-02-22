package com.studiocamera.core.data.media

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory LRU cache for media page responses keyed by (cursor, filter, sort).
 *
 * Avoids re-fetching the same media page from the camera API on every navigation.
 * Evicts least-recently-used entries when the entry count exceeds [maxEntries].
 * Thread-safe via Mutex for coroutine contexts.
 */
class MediaPageCache(
    private val maxEntries: Int = 50
) {
    private val mutex = Mutex()
    private val entries = LinkedHashMap<String, CacheEntry>(32, 0.75f, true)

    data class CacheEntry(
        val data: Any, // MediaPage
        val insertedAtMs: Long
    )

    /** Returns cached entry, or null if not cached or expired (>60s). */
    suspend fun get(key: String): Any? = mutex.withLock {
        val entry = entries[key] ?: return@withLock null
        val age = com.studiocamera.core.common.currentTimeMillis() - entry.insertedAtMs
        if (age > 60_000L) {
            entries.remove(key)
            null
        } else {
            entry.data
        }
    }

    /** Caches a page response, evicting LRU entries if over capacity. */
    suspend fun put(key: String, data: Any) = mutex.withLock {
        entries[key] = CacheEntry(data, com.studiocamera.core.common.currentTimeMillis())

        while (entries.size > maxEntries) {
            val iterator = entries.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
    }

    /** Clears all cached entries. Called when switching cameras. */
    suspend fun clear() = mutex.withLock {
        entries.clear()
    }
}
