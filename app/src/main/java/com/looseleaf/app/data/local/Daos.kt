package com.looseleaf.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Query("SELECT * FROM cached_entries WHERE sync_status != 'PENDING_DELETE' ORDER BY occurred_at DESC")
    fun getAllEntriesFlow(): Flow<List<CachedEntryEntity>>

    @Query("SELECT * FROM cached_entries WHERE sync_status != 'PENDING_DELETE' ORDER BY occurred_at DESC")
    suspend fun getAllEntries(): List<CachedEntryEntity>

    @Query("SELECT * FROM cached_entries WHERE local_id = :localId LIMIT 1")
    suspend fun getEntryByLocalId(localId: Long): CachedEntryEntity?

    @Query("SELECT * FROM cached_entries WHERE server_id = :serverId LIMIT 1")
    suspend fun getEntryByServerId(serverId: Long): CachedEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CachedEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<CachedEntryEntity>)

    @Update
    suspend fun update(entry: CachedEntryEntity)

    @Query("UPDATE cached_entries SET server_id = :serverId, sync_status = 'SYNCED' WHERE local_id = :localId")
    suspend fun markSynced(localId: Long, serverId: Long)

    @Query("DELETE FROM cached_entries WHERE local_id = :localId")
    suspend fun deleteByLocalId(localId: Long)

    @Query("DELETE FROM cached_entries WHERE server_id = :serverId")
    suspend fun deleteByServerId(serverId: Long)

    @Query("DELETE FROM cached_entries")
    suspend fun clearAll()
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM cached_transactions WHERE sync_status != 'PENDING_DELETE' ORDER BY occurred_at DESC")
    fun getAllTransactionsFlow(): Flow<List<CachedTransactionEntity>>

    @Query("SELECT * FROM cached_transactions WHERE sync_status != 'PENDING_DELETE' ORDER BY occurred_at DESC")
    suspend fun getAllTransactions(): List<CachedTransactionEntity>

    @Query("SELECT * FROM cached_transactions WHERE local_id = :localId LIMIT 1")
    suspend fun getTransactionByLocalId(localId: Long): CachedTransactionEntity?

    @Query("SELECT * FROM cached_transactions WHERE server_id = :serverId LIMIT 1")
    suspend fun getTransactionByServerId(serverId: Long): CachedTransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: CachedTransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<CachedTransactionEntity>)

    @Update
    suspend fun update(transaction: CachedTransactionEntity)

    @Query("UPDATE cached_transactions SET server_id = :serverId, sync_status = 'SYNCED' WHERE local_id = :localId")
    suspend fun markSynced(localId: Long, serverId: Long)

    @Query("DELETE FROM cached_transactions WHERE local_id = :localId")
    suspend fun deleteByLocalId(localId: Long)

    @Query("DELETE FROM cached_transactions WHERE server_id = :serverId")
    suspend fun deleteByServerId(serverId: Long)

    @Query("DELETE FROM cached_transactions")
    suspend fun clearAll()
}

@Dao
interface OutboxDao {
    @Query("SELECT * FROM outbox_events ORDER BY created_at ASC")
    suspend fun getAllPendingEvents(): List<OutboxEventEntity>

    @Query("SELECT COUNT(*) FROM outbox_events")
    fun getPendingEventCountFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: OutboxEventEntity)

    @Delete
    suspend fun delete(event: OutboxEventEntity)

    @Query("DELETE FROM outbox_events WHERE event_id = :eventId")
    suspend fun deleteById(eventId: String)

    @Query("DELETE FROM outbox_events WHERE entity_type = :entityType AND local_target_id = :localTargetId")
    suspend fun deleteByLocalTarget(entityType: String, localTargetId: Long)

    @Query("DELETE FROM outbox_events")
    suspend fun clearAll()

    @Query("UPDATE outbox_events SET retry_count = retry_count + 1 WHERE event_id = :eventId")
    suspend fun incrementRetry(eventId: String)
}
