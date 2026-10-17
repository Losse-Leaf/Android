package com.looseleaf.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.looseleaf.app.model.LifeEntry
import com.looseleaf.app.model.Transaction
import com.looseleaf.app.utils.FormatUtils

/**
 * 离线本地生活记录缓存表 (CachedEntry) - docs/mobile.md §4
 */
@Entity(
    tableName = "cached_entries",
    indices = [
        Index(value = ["occurred_at"]),
        Index(value = ["server_id"]),
        Index(value = ["sync_status"])
    ]
)
data class CachedEntryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "local_id") val localId: Long = 0,
    @ColumnInfo(name = "server_id") val serverId: Long? = null,
    @ColumnInfo(name = "type") val type: String = "life",
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "occurred_at") val occurredAt: String = FormatUtils.beijingNowIsoString(),
    @ColumnInfo(name = "location") val location: String? = null,
    @ColumnInfo(name = "city_code") val cityCode: String? = null,
    @ColumnInfo(name = "city_name") val cityName: String? = null,
    @ColumnInfo(name = "attachments_json") val attachmentsJson: String = "[]",
    @ColumnInfo(name = "sync_status") val syncStatus: String = "SYNCED", // 'SYNCED', 'PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE'
    @ColumnInfo(name = "created_at") val createdAt: String = FormatUtils.beijingNowIsoString()
)

/**
 * 离线记账明细缓存表 (CachedTransaction) - docs/mobile.md §4
 * 铁律：金额字段严格为 Long 整型分 (Cents)
 */
@Entity(
    tableName = "cached_transactions",
    indices = [
        Index(value = ["occurred_at"]),
        Index(value = ["server_id"]),
        Index(value = ["sync_status"])
    ]
)
data class CachedTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "local_id") val localId: Long = 0,
    @ColumnInfo(name = "server_id") val serverId: Long? = null,
    @ColumnInfo(name = "amount") val amount: Long, // 单位：分
    @ColumnInfo(name = "type") val type: String = "expense", // 'expense' | 'income'
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "note") val note: String? = null,
    @ColumnInfo(name = "payment_method") val paymentMethod: String? = null,
    @ColumnInfo(name = "occurred_at") val occurredAt: String = FormatUtils.beijingNowIsoString(),
    @ColumnInfo(name = "city_code") val cityCode: String? = null,
    @ColumnInfo(name = "sync_status") val syncStatus: String = "SYNCED",
    @ColumnInfo(name = "created_at") val createdAt: String = FormatUtils.beijingNowIsoString()
)

/**
 * 发件箱事件日志表 (OutboxEvent) - docs/mobile.md §4
 */
@Entity(
    tableName = "outbox_events",
    indices = [
        Index(value = ["created_at"]),
        Index(value = ["entity_type"])
    ]
)
data class OutboxEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "event_id") val eventId: String,
    @ColumnInfo(name = "entity_type") val entityType: String, // 'entry', 'transaction', 'media'
    @ColumnInfo(name = "action") val action: String,           // 'CREATE', 'UPDATE', 'DELETE'
    @ColumnInfo(name = "payload") val payload: String,         // JSON 序列化数据
    @ColumnInfo(name = "local_target_id") val localTargetId: Long = 0,
    @ColumnInfo(name = "server_target_id") val serverTargetId: Long? = null,
    @ColumnInfo(name = "retry_count") val retryCount: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: String = FormatUtils.beijingNowIsoString()
)

/**
 * 离线造物项目缓存表 (CachedProject)
 */
@Entity(tableName = "cached_projects")
data class CachedProjectEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "subtitle") val subtitle: String? = null,
    @ColumnInfo(name = "description") val description: String? = null,
    @ColumnInfo(name = "status") val status: String? = null,
    @ColumnInfo(name = "github_url") val githubUrl: String? = null,
    @ColumnInfo(name = "order") val order: Int? = 0,
    @ColumnInfo(name = "updated_at") val updatedAt: String? = null
)

/**
 * 离线三维心愿缓存表 (CachedWish)
 */
@Entity(tableName = "cached_wishes")
data class CachedWishEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "kind") val kind: String = "item",
    @ColumnInfo(name = "note") val note: String? = null,
    @ColumnInfo(name = "spec") val spec: String? = null,
    @ColumnInfo(name = "budget") val budget: Long = 0,
    @ColumnInfo(name = "actual_cost") val actualCost: Long = 0,
    @ColumnInfo(name = "status") val status: String = "wanting",
    @ColumnInfo(name = "fulfilled_at") val fulfilledAt: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: String? = null
)

/**
 * 离线山川足迹缓存表 (CachedFootprint)
 */
@Entity(tableName = "cached_footprints")
data class CachedFootprintEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "city_code") val cityCode: String,
    @ColumnInfo(name = "city_name") val cityName: String,
    @ColumnInfo(name = "title") val title: String? = null,
    @ColumnInfo(name = "visited_at") val visitedAt: String? = null,
    @ColumnInfo(name = "departed_at") val departedAt: String? = null,
    @ColumnInfo(name = "note") val note: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: String? = null
)
