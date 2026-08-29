package com.aegis.mobile.mesh

/**
 * Transport interface for mesh packet delivery.
 * Aegis uses BLE only. This abstraction allows swapping transport if needed.
 */
interface MeshTransport {
    val id: String

    fun broadcastPacket(packet: ByteArray): Boolean
    fun sendPacketToPeer(peerID: String, packet: ByteArray): Boolean
    fun getDeviceAddressForPeer(peerID: String): String?
    fun start()
    fun stop()
    fun getConnectedDeviceCount(): Int
}
