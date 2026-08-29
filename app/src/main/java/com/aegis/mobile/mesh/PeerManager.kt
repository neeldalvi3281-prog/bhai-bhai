package com.aegis.mobile.mesh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class PeerInfo(
    val id: String,
    var nickname: String,
    var isConnected: Boolean = false,
    var isDirectConnection: Boolean = true,
    var lastSeen: Long = System.currentTimeMillis()
)

interface PeerManagerDelegate {
    fun onPeerListUpdated(peerIDs: List<String>)
    fun onPeerRemoved(peerID: String)
}

class PeerManager(
    private val stalePeerTimeoutMs: Long = 60_000L
) {
    private val peers = ConcurrentHashMap<String, PeerInfo>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    var delegate: PeerManagerDelegate? = null

    init {
        scope.launch {
            while (true) {
                delay(10_000L)
                cleanupStalePeers()
            }
        }
    }

    fun addOrUpdatePeer(peerID: String, nickname: String): Boolean {
        val existing = peers[peerID]
        if (existing != null) {
            existing.nickname = nickname
            existing.lastSeen = System.currentTimeMillis()
            existing.isConnected = true
            return false
        }

        // Stale nickname dedup: if same nickname exists with different ID and old one is stale, evict
        val staleMatch = peers.values.find {
            it.nickname == nickname && it.id != peerID &&
                    System.currentTimeMillis() - it.lastSeen > 10_000L
        }
        if (staleMatch != null) {
            peers.remove(staleMatch.id)
            delegate?.onPeerRemoved(staleMatch.id)
        }

        peers[peerID] = PeerInfo(id = peerID, nickname = nickname)
        notifyPeerListUpdated()
        return true
    }

    fun removePeer(peerID: String) {
        val removed = peers.remove(peerID) ?: return
        removed.isConnected = false
        delegate?.onPeerRemoved(peerID)
        notifyPeerListUpdated()
    }

    fun getPeerInfo(peerID: String): PeerInfo? = peers[peerID]

    fun getActivePeerIDs(): List<String> =
        peers.values.filter { it.isConnected }.map { it.id }.sorted()

    fun getActivePeerCount(): Int =
        peers.values.count { it.isConnected }

    fun getAllPeerIDs(): List<String> = peers.keys().toList()

    fun updatePeerLastSeen(peerID: String) {
        peers[peerID]?.lastSeen = System.currentTimeMillis()
    }

    fun updatePeerRSSI(peerID: String, rssi: Int) {
        // Store if needed for connection quality; for MVP just update lastSeen
        updatePeerLastSeen(peerID)
    }

    fun markPeerConnected(peerID: String) {
        peers[peerID]?.let {
            it.isConnected = true
            it.isDirectConnection = true
            it.lastSeen = System.currentTimeMillis()
        }
        notifyPeerListUpdated()
    }

    fun markPeerDisconnected(peerID: String) {
        peers[peerID]?.let {
            it.isConnected = false
        }
        notifyPeerListUpdated()
    }

    fun clearAllPeers() {
        peers.clear()
        delegate?.onPeerListUpdated(emptyList())
    }

    fun shutdown() {
        scope.cancel()
        peers.clear()
    }

    private fun cleanupStalePeers() {
        val now = System.currentTimeMillis()
        val stale = peers.values.filter { now - it.lastSeen > stalePeerTimeoutMs }
        stale.forEach { peer ->
            peers.remove(peer.id)
            peer.isConnected = false
            delegate?.onPeerRemoved(peer.id)
        }
        if (stale.isNotEmpty()) notifyPeerListUpdated()
    }

    private fun notifyPeerListUpdated() {
        delegate?.onPeerListUpdated(getActivePeerIDs())
    }
}
