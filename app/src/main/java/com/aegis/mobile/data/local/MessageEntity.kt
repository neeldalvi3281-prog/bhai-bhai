package com.aegis.mobile.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MessageType {
    SOS, BROADCAST, PRIVATE, COMMAND_CENTER
}

enum class SyncStatus {
    PENDING, SYNCED, FAILED
}

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["message_type"]),
        Index(value = ["sync_status"]),
        Index(value = ["destination_device_id"]),
        Index(value = ["origin_device_id"]),
        Index(value = ["created_at"])
    ]
)
data class MessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "message_id")
    val messageId: String,

    @ColumnInfo(name = "message_type")
    val messageType: MessageType,

    @ColumnInfo(name = "origin_device_id")
    val originDeviceId: String,

    @ColumnInfo(name = "destination_device_id")
    val destinationDeviceId: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "latitude")
    val latitude: Double? = null,

    @ColumnInfo(name = "longitude")
    val longitude: Double? = null,

    @ColumnInfo(name = "text")
    val text: String? = null,

    @ColumnInfo(name = "payload")
    val payload: ByteArray? = null,

    @ColumnInfo(name = "ttl")
    val ttl: Int? = null,

    @ColumnInfo(name = "severity")
    val severity: String? = null,

    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING,

    @ColumnInfo(name = "received_at")
    val receivedAt: Long? = null,

    @ColumnInfo(name = "last_forwarded_at")
    val lastForwardedAt: Long? = null,

    @ColumnInfo(name = "is_uploaded")
    val isUploaded: Boolean = false,

    @ColumnInfo(name = "is_delivered")
    val isDelivered: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MessageEntity) return false
        return messageId == other.messageId
    }

    override fun hashCode(): Int = messageId.hashCode()
}
