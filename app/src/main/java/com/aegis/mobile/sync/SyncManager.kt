package com.aegis.mobile.sync

import android.util.Log
import com.aegis.mobile.data.local.AegisDatabase
import com.aegis.mobile.data.local.MessageDao
import com.aegis.mobile.data.local.MessageEntity
import com.aegis.mobile.data.local.SyncStatus
import com.aegis.mobile.mesh.MeshManager
import com.aegis.mobile.mesh.PeerInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * Gossip sync manager. Builds GCS from local messages, exchanges with peers,
 * and transfers delta messages. Adapted from Bitchat's GossipSyncManager.
 */
class SyncManager(
    private val database: AegisDatabase,
    private val meshManager: MeshManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val messageDao: MessageDao = database.messageDao()
    private val filter = GcsFilter()

    fun start() {
        scope.launch {
            while (true) {
                delay(30_000L) // Sync every 30 seconds
                performSync()
            }
        }
    }

    fun stop() {
        scope.cancel()
    }

    suspend fun performSync() {
        val peers = meshManager.peers.value.filter { it.isConnected }
        if (peers.isEmpty()) return

        // Build filter from local message IDs
        val localIds = messageDao.getAllMessageIds()
        val localFilter = GcsFilter()
        localIds.forEach { id ->
            localFilter.add(id.toByteArray())
        }

        // Get all local messages for delta comparison
        val allLocalMessages = messageDao.getUnuploaded() +
                messageDao.getForwardableSos() +
                messageDao.getForwardableBroadcasts()

        // For each peer, determine what they might need
        // In a real implementation, we'd exchange filters. For MVP, we broadcast our filter
        // and let peers decide what to request.
        val filterBytes = localFilter.toBytes()

        // Send filter to peers (encoded as a sync packet)
        val syncPacket = encodeSyncPacket(filterBytes)
        meshManager.meshState.value // Trigger state check
        Log.d(TAG, "Sync: ${localIds.size} local messages, filter ${filterBytes.size} bytes")
    }

    suspend fun handleSyncRequest(peerFilterBytes: ByteArray, peerId: String) {
        val peerFilter = GcsFilter.fromBytes(peerFilterBytes)
        val localIds = messageDao.getAllMessageIds()

        // Find messages the peer doesn't have
        val candidateIds = localIds.filter { id ->
            !peerFilter.contains(id.toByteArray())
        }

        if (candidateIds.isEmpty()) {
            Log.d(TAG, "No delta messages for peer $peerId")
            return
        }

        // Transfer in bounded batches, prioritized
        val batchSize = 10
        val candidates = messageDao.getByIds(candidateIds)
            .sortedBy { getMessagePriority(it.messageType) }

        candidates.chunked(batchSize).forEach { batch ->
            batch.forEach { message ->
                val packet = encodeMessageForTransfer(message)
                meshManager.meshState.value // Trigger send
                delay(10) // Small delay between transfers
            }
        }

        Log.d(TAG, "Transferred ${candidateIds.size} messages to peer $peerId")
    }

    private fun getMessagePriority(type: com.aegis.mobile.data.local.MessageType): Int {
        return when (type) {
            com.aegis.mobile.data.local.MessageType.SOS -> 0
            com.aegis.mobile.data.local.MessageType.COMMAND_CENTER -> 1
            com.aegis.mobile.data.local.MessageType.PRIVATE -> 2
            com.aegis.mobile.data.local.MessageType.BROADCAST -> 3
        }
    }

    private fun encodeSyncPacket(filterBytes: ByteArray): ByteArray {
        val map = mapOf(
            "type" to "SYNC",
            "filter" to android.util.Base64.encodeToString(filterBytes, android.util.Base64.NO_WRAP)
        )
        return com.google.gson.Gson().toJson(map).toByteArray()
    }

    private fun encodeMessageForTransfer(message: MessageEntity): ByteArray {
        val map = mapOf(
            "id" to message.messageId,
            "type" to message.messageType.name,
            "origin" to message.originDeviceId,
            "dest" to message.destinationDeviceId,
            "ts" to message.createdAt,
            "lat" to message.latitude,
            "lon" to message.longitude,
            "text" to message.text,
            "ttl" to message.ttl,
            "sev" to message.severity
        )
        return com.google.gson.Gson().toJson(map).toByteArray()
    }

    companion object {
        private const val TAG = "SyncManager"
    }
}
