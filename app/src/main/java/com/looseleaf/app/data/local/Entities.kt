package com.looseleaf.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.looseleaf.app.utils.FormatUtils

/**
 * 离线本地生活记录缓存表 (CachedEntry) - docs/mobile.md §4
 */
@Entity(
    tableName = "cached_entries",
    indices = [
        Index(value = ["occurred_at"]),
        Index(value = ["server_id"]),
        Index(value = ["sync_status"]),
        // MOB-P1-47：主列表查询是 `WHERE sync_status != 'PENDING_DELETE' ORDER BY occurred_at DESC`，
        // 单列索引无法同时覆盖过滤与排序，补一条复合索引让 SQLite 走索引扫描。
        Index(value = ["sync_status", "occurred_at"])
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
    // 以下两列保留以兼容既有 Room v12 schema，业务层不再读写
    @ColumnInfo(name = "project_id") val projectId: Long? = null,
    @ColumnInfo(name = "wish_id") val wishId: Long? = null,
    @ColumnInfo(name = "footprint_id") val footprintId: Long? = null,
    @ColumnInfo(name = "is_private", defaultValue = "0") val isPrivate: Boolean = false,
    @ColumnInfo(name = "attachments_json") val attachmentsJson: String = "[]",
    @ColumnInfo(name = "sync_status") val syncStatus: String = "SYNCED", // 'SYNCED', 'PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE'
    @ColumnInfo(name = "created_at") val createdAt: String = FormatUtils.beijingNowIsoString(),
    // v13：与 Vesper Space 共享的原生分类；空串 = 日常。
    // Room 的 defaultValue 是 SQL 表达式，空字符串字面量必须写成 "''"，与迁移里的 DEFAULT '' 对齐。
    @ColumnInfo(name = "category", defaultValue = "''") val category: String = ""
)

/**
 * 发件箱事件日志表 (OutboxEvent) - docs/mobile.md §4
 */
@Entity(
    tableName = "outbox_events",
    indices = [
        Index(value = ["created_at"]),
        Index(value = ["entity_type"]),
        Index(value = ["failed_at"]),
        // MOB-P1-46：findEventByTarget 按 (entity_type, local_target_id, action) 查询，
        // 原先没有 local_target_id 索引 —— 每次编辑未同步条目都只能全表扫描。
        Index(value = ["entity_type", "local_target_id", "action"])
    ]
)
data class OutboxEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "event_id") val eventId: String,
    @ColumnInfo(name = "entity_type") val entityType: String, // 'entry', 'media_entry', 'wish'
    @ColumnInfo(name = "action") val action: String,           // 'CREATE', 'UPDATE', 'DELETE', 'RESOLVE'
    @ColumnInfo(name = "payload") val payload: String,         // JSON 序列化数据
    @ColumnInfo(name = "local_target_id") val localTargetId: Long = 0,
    @ColumnInfo(name = "server_target_id") val serverTargetId: Long? = null,
    @ColumnInfo(name = "retry_count") val retryCount: Int = 0,
    /** 进入失败态的时间。非空表示该事件已停止自动重试，等待用户手动重试或放弃。 */
    @ColumnInfo(name = "failed_at") val failedAt: String? = null,
    /** 最后一次失败原因，用于诊断与展示。 */
    @ColumnInfo(name = "last_error") val lastError: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String = FormatUtils.beijingNowIsoString()
)

/**
 * 离线心愿单缓存表 (CachedWish)
 */
@Entity(tableName = "cached_wishes")
data class CachedWishEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "kind") val kind: String = "item",
    @ColumnInfo(name = "note") val note: String? = null,
    @ColumnInfo(name = "spec") val spec: String? = null,
    @ColumnInfo(name = "city_code") val cityCode: String? = null,
    @ColumnInfo(name = "city_name") val cityName: String? = null,
    @ColumnInfo(name = "budget") val budget: Long = 0,
    @ColumnInfo(name = "actual_cost") val actualCost: Long = 0,
    @ColumnInfo(name = "resolution_type") val resolutionType: String? = null,
    @ColumnInfo(name = "actual_title") val actualTitle: String? = null,
    @ColumnInfo(name = "status") val status: String = "wanting",
    @ColumnInfo(name = "target_date") val targetDate: String? = null,
    @ColumnInfo(name = "fulfilled_at") val fulfilledAt: String? = null,
    @ColumnInfo(name = "gear_id") val gearId: String? = null,
    @ColumnInfo(name = "is_private", defaultValue = "0") val isPrivate: Boolean = false,
    @ColumnInfo(name = "attachments_json") val attachmentsJson: String = "[]",
    @ColumnInfo(name = "updated_at") val updatedAt: String? = null
)
