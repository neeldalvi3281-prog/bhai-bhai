package com.aegis.mobile.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aegis_device")

object DeviceIdManager {

    private val DEVICE_ID_KEY = stringPreferencesKey("device_id")

    suspend fun getDeviceId(context: Context): String {
        val prefs = context.dataStore.data.first()
        val existing = prefs[DEVICE_ID_KEY]
        if (existing != null) return existing

        val newId = UUID.randomUUID().toString()
        context.dataStore.edit { it[DEVICE_ID_KEY] = newId }
        return newId
    }

    suspend fun getShortDeviceId(context: Context): String {
        val fullId = getDeviceId(context)
        return fullId.take(8)
    }
}
