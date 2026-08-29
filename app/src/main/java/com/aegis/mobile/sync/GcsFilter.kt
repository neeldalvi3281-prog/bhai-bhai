package com.aegis.mobile.sync

import android.util.Log
import java.security.MessageDigest

/**
 * Golomb-Coded Set filter for gossip sync.
 * Adapted from Bitchat's GCSFilter.kt for Aegis.
 * Used to identify candidate delta messages during peer sync.
 */
class GcsFilter(
    private val targetFpr: Double = 0.01, // 1% false positive rate
    private val maxBytes: Int = 400
) {
    private val p: Int // Golomb-Rice parameter
    private val m: Long // Range size

    init {
        p = kotlin.math.ceil(kotlin.math.ln(1.0 / targetFpr) / kotlin.math.ln(2.0)).toInt()
        m = (maxBytes.toLong() * 8) / p // Approximate capacity
    }

    private val items = mutableListOf<Long>()
    private var sorted = false

    fun add(packetId: ByteArray) {
        val hash = hashToLong(packetId)
        items.add(hash)
        sorted = false
    }

    fun contains(packetId: ByteArray): Boolean {
        val hash = hashToLong(packetId)
        if (!sorted) {
            items.sort()
            sorted = true
        }
        // Binary search for approximate membership
        var low = 0
        var high = items.size - 1
        while (low <= high) {
            val mid = (low + high) / 2
            when {
                items[mid] == hash -> return true
                items[mid] < hash -> low = mid + 1
                else -> high = mid - 1
            }
        }
        return false
    }

    fun getDelta(remoteFilter: GcsFilter): List<Long> {
        if (!sorted) {
            items.sort()
            sorted = true
        }
        return items.filter { !remoteFilter.containsFromHash(it) }
    }

    private fun containsFromHash(hash: Long): Boolean {
        var low = 0
        var high = items.size - 1
        while (low <= high) {
            val mid = (low + high) / 2
            when {
                items[mid] == hash -> return true
                items[mid] < hash -> low = mid + 1
                else -> high = mid - 1
            }
        }
        return false
    }

    fun toBytes(): ByteArray {
        if (!sorted) {
            items.sort()
            sorted = true
        }
        // Simple encoding: sorted hashes as 8-byte longs
        val result = ByteArray(items.size * 8)
        items.forEachIndexed { index, hash ->
            val bytes = ByteArray(8)
            var h = hash
            for (i in 7 downTo 0) {
                bytes[i] = (h and 0xFF).toByte()
                h = h shr 8
            }
            System.arraycopy(bytes, 0, result, index * 8, 8)
        }
        return result
    }

    companion object {
        fun fromBytes(data: ByteArray, targetFpr: Double = 0.01, maxBytes: Int = 400): GcsFilter {
            val filter = GcsFilter(targetFpr, maxBytes)
            var i = 0
            while (i + 8 <= data.size) {
                var hash = 0L
                for (j in 0 until 8) {
                    hash = (hash shl 8) or (data[i + j].toLong() and 0xFF)
                }
                filter.items.add(hash)
                i += 8
            }
            filter.sorted = true
            return filter
        }

        private fun hashToLong(data: ByteArray): Long {
            val digest = MessageDigest.getInstance("SHA-256").digest(data)
            var hash = 0L
            for (i in 0 until 8) {
                hash = (hash shl 8) or (digest[i].toLong() and 0xFF)
            }
            return hash and Long.MAX_VALUE // Ensure positive
        }
    }
}
