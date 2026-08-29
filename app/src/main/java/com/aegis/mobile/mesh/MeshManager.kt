package com.aegis.mobile.mesh

import android.content.Context
import android.util.Log
import com.aegis.mobile.data.local.AegisDatabase
import com.aegis.mobile.data.local.DeviceIdManager
import com.aegis.mobile.data.local.MessageDao
import com.aegis.mobile.data.local.MessageEntity
import com.aegis.mobile.data.local.MessageType
import com.aegis.mobile.data.local.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Central mesh coordinator. Manages BLE transport, peer lifecycle, and message relay.
 * Adapted from Bitchat's MeshCore but drastically simplified for Aegis MVP.
 */
class MeshManager(
    private val context: Context,
    private val database: AegisDatabase
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val messageDao: MessageDao = database.messageDao()

    private var myDeviceId: String = ""
    private lateinit var bleTransport: BleMeshService
    private val peerManager = PeerManager()

    private val _meshState = MutableStateFlow(MeshState())
    val meshState: StateFlow<MeshState> = _meshState

    private val _peers = MutableStateFlow<List<PeerInfo>>(emptyList())
    val peers: StateFlow<List<PeerInfo>> = _peers

    data class MeshState(
        val isActive: Boolean = false,
        val peerCount: Int = 0,
        val storedMessages: Int = 0,
        val pendingMessages: Int = 0
    )

    suspend fun start() {
        myDeviceId = DeviceIdManager.getDeviceId(context)
        bleTransport = BleMeshService(context, myDeviceId)

        peerManager.delegate = object : PeerManagerDelegate {
            override fun onPeerListUpdated(peerIDs: List<String>) {
                _peers.value = peerIDs.mapNotNull { peerManager.getPeerInfo(it) }
                updateMeshState()
            }

            override fun onPeerRemoved(peerID: String) {
                updateMeshState()
            }
        }

        bleTransport.setDataCallback(object : BleMeshService.BleDataCallback {
            override fun onDataReceived(data: ByteArray, deviceAddress: String, peerID: String?) {
                handleReceivedPacket(data, deviceAddress)
            }

            override fun onDeviceConnected(deviceAddress: String) {
                peerManager.addOrUpdatePeer(deviceAddress, deviceAddress.take(8))
                peerManager.markPeerConnected(deviceAddress)
                Log.i(TAG, "Peer connected: $deviceAddress")
            }

            override fun onDeviceDisconnected(deviceAddress: String) {
                peerManager.markPeerDisconnected(deviceAddress)
                Log.i(TAG, "Peer disconnected: $deviceAddress")
            }
        })

        bleTransport.start()
        _meshState.value = MeshState(isActive = true)
        Log.i(TAG, "Mesh started with device ID: ${myDeviceId.take(8)}")
    }

    fun stop() {
        bleTransport.stop()
        peerManager.shutdown()
        scope.cancel()
        _meshState.value = MeshState()
    }

    suspend fun sendMessage(
        messageType: MessageType,
        text: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        destinationDeviceId: String? = null,
        ttl: Int = 7,
        severity: String? = null
    ): String {
        val messageId = UUID.randomUUID().toString()
        val message = MessageEntity(
            messageId = messageId,
            messageType = messageType,
            originDeviceId = myDeviceId,
            destinationDeviceId = destinationDeviceId,
            createdAt = System.currentTimeMillis(),
            latitude = latitude,
            longitude = longitude,
            text = text,
            ttl = ttl,
            severity = severity,
            syncStatus = SyncStatus.PENDING
        )

        messageDao.insert(message)
        broadcastMessage(message)
        updateMeshState()
        return messageId
    }

    private fun broadcastMessage(message: MessageEntity) {
        val packet = encodeMessage(message)
        bleTransport.broadcastPacket(packet)
    }

    private fun handleReceivedPacket(data: ByteArray, deviceAddress: String) {
        scope.launch {
            try {
                val message = decodeMessage(data) ?: return@launch

                // Atomic dedup: INSERT OR IGNORE
                val inserted = messageDao.insert(message)
                if (inserted == -1L) {
                    Log.d(TAG, "Duplicate message ignored: ${message.messageId.take(8)}")
                    return@launch
                }

                // Relay if TTL > 0 and not addressed to us
                if (message.ttl != null && message.ttl > 0 && message.destinationDeviceId != myDeviceId) {
                    relayMessage(message)
                }

                updateMeshState()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to handle packet: ${e.message}")
            }
        }
    }

    private suspend fun relayMessage(message: MessageEntity) {
        val newTtl = (message.ttl ?: 0) - 1
        if (newTtl <= 0) return

        val relayed = message.copy(
            ttl = newTtl,
            lastForwardedAt = System.currentTimeMillis()
        )
        val packet = encodeMessage(relayed)
        bleTransport.broadcastPacket(packet)
    }

    private fun encodeMessage(message: MessageEntity): ByteArray {
        // Simple JSON encoding via Gson
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

    private fun decodeMessage(data: ByteArray): MessageEntity? {
        return try {
            val json = String(data)
            val map = com.google.gson.Gson().fromJson(json, Map::class.java) as? Map<*, *> ?: return null
            MessageEntity(
                messageId = map["id"] as? String ?: return null,
                messageType = MessageType.valueOf(map["type"] as? String ?: "BROADCAST"),
                originDeviceId = map["origin"] as? String ?: return null,
                destinationDeviceId = map["dest"] as? String,
                createdAt = (map["ts"] as? Double)?.toLong() ?: System.currentTimeMillis(),
                latitude = (map["lat"] as? Double),
                longitude = (map["lon"] as? Double),
                text = map["text"] as? String,
                ttl = (map["ttl"] as? Double)?.toInt(),
                severity = map["sev"] as? String,
                syncStatus = SyncStatus.PENDING,
                receivedAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode message: ${e.message}")
            null
        }
    }

    private fun updateMeshState() {
        scope.launch {
            val storedCount = messageDao.observeCount()
            val pendingCount = messageDao.observePendingCount()
            storedCount.collect { stored ->
                pendingCount.collect { pending ->
                    _meshState.value = MeshState(
                        isActive = true,
                        peerCount = peerManager.getActivePeerCount(),
                        storedMessages = stored,
                        pendingMessages = pending
                    )
                }
            }
        }
    }

    fun getMyDeviceId(): String = myDeviceId

    companion object {
        private const val TAG = "MeshManager"
    }
}
