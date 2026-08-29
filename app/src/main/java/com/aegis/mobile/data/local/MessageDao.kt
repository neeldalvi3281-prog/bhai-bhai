package com.aegis.mobile.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE message_id = :messageId")
    suspend fun getById(messageId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE message_id = :messageId")
    fun observeById(messageId: String): Flow<MessageEntity?>

    @Query("SELECT * FROM messages ORDER BY created_at DESC")
    fun observeAll(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE message_type = :type ORDER BY created_at DESC")
    fun observeByType(type: MessageType): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE sync_status = :status ORDER BY created_at DESC")
    fun observeBySyncStatus(status: SyncStatus): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE destination_device_id = :deviceId OR destination_device_id IS NULL ORDER BY created_at DESC")
    fun observeForDevice(deviceId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE is_uploaded = 0 ORDER BY created_at ASC")
    suspend fun getUnuploaded(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE is_uploaded = 0 AND (message_type = 'SOS' OR message_type = 'COMMAND_CENTER') ORDER BY created_at ASC")
    suspend fun getUnuploadedPriority(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE message_id IN (:messageIds)")
    suspend fun getByIds(messageIds: List<String>): List<MessageEntity>

    @Query("UPDATE messages SET is_uploaded = 1 WHERE message_id IN (:messageIds)")
    suspend fun markUploaded(messageIds: List<String>)

    @Query("UPDATE messages SET is_delivered = 1 WHERE message_id = :messageId")
    suspend fun markDelivered(messageId: String)

    @Query("SELECT * FROM messages WHERE message_type = 'BROADCAST' AND ttl > 0 ORDER BY created_at ASC")
    suspend fun getForwardableBroadcasts(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE message_type = 'PRIVATE' AND destination_device_id != :excludeDeviceId ORDER BY created_at ASC")
    suspend fun getForwardablePrivate(excludeDeviceId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE message_type = 'SOS' ORDER BY created_at ASC")
    suspend fun getForwardableSos(): List<MessageEntity>

    @Query("SELECT message_id FROM messages")
    suspend fun getAllMessageIds(): List<String>

    @Query("SELECT COUNT(*) FROM messages")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE is_uploaded = 0")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE is_delivered = 0")
    fun observeUndeliveredCount(): Flow<Int>

    @Query("DELETE FROM messages WHERE message_id = :messageId")
    suspend fun delete(messageId: String)
}
