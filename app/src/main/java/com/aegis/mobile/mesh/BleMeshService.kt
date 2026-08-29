package com.aegis.mobile.mesh

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * BLE mesh transport using GATT server (peripheral) and GATT client (central).
 * Adapted from Bitchat's BluetoothGattServerManager + BluetoothGattClientManager.
 * Simplified for Aegis: no Noise handshake, no favorites, no source routing.
 */
class BleMeshService(
    private val context: Context,
    private val myPeerID: String
) : MeshTransport {

    override val id: String = "ble"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private var gattServer: BluetoothGattServer? = null
    private var isAdvertising = false
    private var isScanning = false
    private val connectedDevices = mutableMapOf<String, BluetoothDevice>()

    // Aegis service UUID - must be unique to avoid collision with Bitchat
    val SERVICE_UUID: UUID = UUID.fromString("A1B2C3D4-E5F6-4A5B-8C9D-0E1F2A3B4C5D")
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("B2C3D4E5-F6A7-4B5C-9D0E-1F2A3B4C5D6E")

    private var dataCallback: BleDataCallback? = null

    interface BleDataCallback {
        fun onDataReceived(data: ByteArray, deviceAddress: String, peerID: String?)
        fun onDeviceConnected(deviceAddress: String)
        fun onDeviceDisconnected(deviceAddress: String)
    }

    fun setDataCallback(callback: BleDataCallback) {
        this.dataCallback = callback
    }

    override fun start() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w(TAG, "Bluetooth not available or disabled")
            return
        }
        startGattServer()
        startAdvertising()
        startScanning()
    }

    override fun stop() {
        stopScanning()
        stopAdvertising()
        stopGattServer()
        scope.cancel()
    }

    override fun getConnectedDeviceCount(): Int = connectedDevices.size

    override fun broadcastPacket(packet: ByteArray): Boolean {
        if (connectedDevices.isEmpty()) return false
        var sent = false
        connectedDevices.values.forEach { device ->
            if (sendToDevice(device, packet)) sent = true
        }
        return sent
    }

    override fun sendPacketToPeer(peerID: String, packet: ByteArray): Boolean {
        // For BLE, we send to all connected devices; the relay layer handles dedup
        return broadcastPacket(packet)
    }

    override fun getDeviceAddressForPeer(peerID: String): String? {
        return connectedDevices.keys.find { it == peerID }
    }

    @SuppressLint("MissingPermission")
    private fun startGattServer() {
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(characteristic)
        gattServer?.addService(service)
        Log.i(TAG, "GATT server started")
    }

    @SuppressLint("MissingPermission")
    private fun stopGattServer() {
        gattServer?.close()
        gattServer = null
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser ?: return
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(android.os.ParcelUuid(SERVICE_UUID))
            .setIncludeDeviceName(false)
            .build()

        advertiser.startAdvertising(settings, data, advertiseCallback)
    }

    @SuppressLint("MissingPermission")
    private fun stopAdvertising() {
        bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        isAdvertising = false
    }

    @SuppressLint("MissingPermission")
    private fun startScanning() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        val settings = android.bluetooth.le.ScanSettings.Builder()
            .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val filter = android.bluetooth.le.ScanFilter.Builder()
            .setServiceUuid(android.os.ParcelUuid(SERVICE_UUID))
            .build()

        scanner.startScan(listOf(filter), settings, scanCallback)
        isScanning = true

        // Self-healing scan restart
        scope.launch {
            while (true) {
                delay(30_000L)
                if (isScanning) {
                    try {
                        scanner.stopScan(scanCallback)
                        delay(1_000L)
                        scanner.startScan(listOf(filter), settings, scanCallback)
                    } catch (e: Exception) {
                        Log.w(TAG, "Scan restart failed: ${e.message}")
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScanning() {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        if (connectedDevices.containsKey(device.address)) return
        device.connectGatt(context, false, gattClientCallback)
    }

    @SuppressLint("MissingPermission")
    private fun sendToDevice(device: BluetoothDevice, data: ByteArray): Boolean {
        val gatt = device.javaClass.getMethod("getGatt").invoke(device) as? BluetoothGatt ?: return false
        val service = gatt.getService(SERVICE_UUID) ?: return false
        val characteristic = service.getCharacteristic(CHARACTERISTIC_UUID) ?: return false
        characteristic.value = data
        return gatt.writeCharacteristic(characteristic)
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevices[device.address] = device
                    dataCallback?.onDeviceConnected(device.address)
                    Log.i(TAG, "Device connected: ${device.address}")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedDevices.remove(device.address)
                    dataCallback?.onDeviceDisconnected(device.address)
                    Log.i(TAG, "Device disconnected: ${device.address}")
                }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (characteristic?.uuid == CHARACTERISTIC_UUID && value != null && device != null) {
                dataCallback?.onDataReceived(value, device.address, null)
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }
    }

    private val gattClientCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevices[gatt.device.address] = gatt.device
                    gatt.discoverServices()
                    dataCallback?.onDeviceConnected(gatt.device.address)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedDevices.remove(gatt.device.address)
                    dataCallback?.onDeviceDisconnected(gatt.device.address)
                    gatt.close()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            // Services discovered, ready for writes
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            // Write complete
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            isAdvertising = true
            Log.i(TAG, "Advertising started")
        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            Log.w(TAG, "Advertising failed: $errorCode")
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                if (!connectedDevices.containsKey(device.address)) {
                    connectToDevice(device)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            Log.w(TAG, "Scan failed: $errorCode")
        }
    }

    companion object {
        private const val TAG = "BleMeshService"
    }
}
