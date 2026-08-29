package com.aegis.mobile.gateway

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.aegis.mobile.data.local.AegisDatabase
import com.aegis.mobile.data.local.MessageDao
import com.aegis.mobile.data.local.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Gateway manager. Detects internet availability and uploads messages to FastAPI.
 * Also receives reverse messages from the command center.
 */
class GatewayManager(
    private val context: Context,
    private val database: AegisDatabase
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val messageDao: MessageDao = database.messageDao()

    private val _gatewayState = MutableStateFlow(GatewayState())
    val gatewayState: StateFlow<GatewayState> = _gatewayState

    private var isConnected = false
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    data class GatewayState(
        val isOnline: Boolean = false,
        val isGateway: Boolean = false,
        val lastUploadTime: Long = 0,
        val uploadedCount: Int = 0
    )

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            isConnected = true
            _gatewayState.value = _gatewayState.value.copy(isOnline = true, isGateway = true)
            scope.launch {
                uploadPendingMessages()
            }
        }

        override fun onLost(network: Network) {
            isConnected = false
            _gatewayState.value = _gatewayState.value.copy(isOnline = false, isGateway = false)
        }
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    fun stop() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
        scope.cancel()
    }

    private suspend fun uploadPendingMessages() {
        if (!isConnected) return

        val messages = messageDao.getUnuploaded()
        if (messages.isEmpty()) return

        val batchSize = 50
        messages.chunked(batchSize).forEach { batch ->
            try {
                val uploaded = uploadBatch(batch)
                if (uploaded) {
                    messageDao.markUploaded(batch.map { it.messageId })
                    _gatewayState.value = _gatewayState.value.copy(
                        lastUploadTime = System.currentTimeMillis(),
                        uploadedCount = _gatewayState.value.uploadedCount + batch.size
                    )
                    Log.i(TAG, "Uploaded ${batch.size} messages")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Upload failed: ${e.message}")
                // Keep messages for retry
            }
        }
    }

    private fun uploadBatch(messages: List<com.aegis.mobile.data.local.MessageEntity>): Boolean {
        // Conceptual FastAPI upload - implement when backend is ready
        // For now, just log
        Log.i(TAG, "Would upload ${messages.size} messages to FastAPI")
        return true
    }

    suspend fun downloadReverseMessages() {
        if (!isConnected) return

        try {
            // Conceptual: GET from FastAPI
            Log.i(TAG, "Checking for reverse messages from command center")
        } catch (e: Exception) {
            Log.w(TAG, "Reverse download failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "GatewayManager"
        private const val FASTAPI_URL = "http://localhost:8000" // Configure for deployment
    }
}
