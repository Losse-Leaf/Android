package com.looseleaf.feature.ledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 账本独立 Room 本地数据库 (LedgerDatabase)
 * 数据库文件：looseleaf_ledger.db
 *
 * 遵循架构规范：
 * 账本数据完全独立存储与维护，与时间流 (AppDatabase) 彻底解耦，
 * 可随时独立成为单独 App 而无需动及散页主壳架构。
 */
@Database(
    entities = [
        CachedTransactionEntity::class,
        LedgerOutboxEventEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun outboxDao(): LedgerOutboxDao

    companion object {
        private const val DB_NAME = "looseleaf_ledger.db"

        @Volatile
        private var instance: LedgerDatabase? = null

        fun getInstance(context: Context): LedgerDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LedgerDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
