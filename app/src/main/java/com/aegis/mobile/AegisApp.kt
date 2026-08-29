package com.aegis.mobile

import android.app.Application
import android.util.Log
import com.aegis.mobile.data.local.AegisDatabase
import com.aegis.mobile.gateway.GatewayManager
import com.aegis.mobile.mesh.MeshManager
import com.aegis.mobile.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AegisApp : Application() {

    val database: AegisDatabase by lazy { AegisDatabase.getInstance(this) }
    val meshManager: MeshManager by lazy { MeshManager(this, database) }
    val syncManager: SyncManager by lazy { SyncManager(database, meshManager) }
    val gatewayManager: GatewayManager by lazy { GatewayManager(this, database) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        instance = this

        scope.launch {
            try {
                meshManager.start()
                syncManager.start()
                gatewayManager.start()
                Log.i(TAG, "Aegis system started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Aegis: ${e.message}", e)
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        meshManager.stop()
        syncManager.stop()
        gatewayManager.stop()
    }

    companion object {
        private const val TAG = "AegisApp"
        lateinit var instance: AegisApp
            private set
    }
}
