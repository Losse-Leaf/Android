package com.looseleaf.feature.ledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 3,
    exportSchema = true
)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun outboxDao(): LedgerOutboxDao

    companion object {
        private const val DB_NAME = "looseleaf_ledger.db"

        @Volatile
        private var instance: LedgerDatabase? = null

        /**
         * 2 → 3 迁移：
         * 1. 为 cached_transactions 添加 deleted_at 与 updated_at 字段。
         * 2. 清洗存量重复 server_id（保留未同步改动优先，否则保留最新修改行）。
         * 3. 建立 server_id 唯一索引 (UNIQUE INDEX)。
         * 4. 重构 ledger_outbox_events 表，添加 sequence、client_request_id 与 enqueued_at，
         *    修复历史 'null' 字符串死信，为可靠同步与幂等查重奠定基础。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. cached_transactions 添加字段
                db.execSQL("ALTER TABLE `cached_transactions` ADD COLUMN `deleted_at` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `cached_transactions` ADD COLUMN `updated_at` TEXT DEFAULT NULL")

                // 2. 清洗可能存在的重复 server_id
                db.execSQL(
                    "DELETE FROM `cached_transactions` " +
                        "WHERE `server_id` IS NOT NULL " +
                        "AND `local_id` NOT IN (" +
                        "  SELECT t1.`local_id` FROM `cached_transactions` AS t1 " +
                        "  WHERE t1.`server_id` IS NOT NULL " +
                        "  AND NOT EXISTS (" +
                        "    SELECT 1 FROM `cached_transactions` AS t2 " +
                        "    WHERE t2.`server_id` = t1.`server_id` " +
                        "    AND (" +
                        "      (t2.`sync_status` != 'SYNCED' AND t1.`sync_status` = 'SYNCED') " +
                        "      OR (t2.`sync_status` = t1.`sync_status` AND t2.`local_id` > t1.`local_id`)" +
                        "    )" +
                        "  )" +
                        ")"
                )

                // 3. 重建 server_id 唯一索引
                db.execSQL("DROP INDEX IF EXISTS `index_cached_transactions_server_id`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_cached_transactions_server_id` ON `cached_transactions` (`server_id`)")

                // 4. 重构 ledger_outbox_events 表
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ledger_outbox_events_new` (" +
                        "`event_id` TEXT NOT NULL PRIMARY KEY, " +
                        "`action` TEXT NOT NULL, " +
                        "`payload` TEXT NOT NULL, " +
                        "`local_target_id` INTEGER NOT NULL, " +
                        "`server_target_id` INTEGER, " +
                        "`client_request_id` TEXT NOT NULL, " +
                        "`sequence` INTEGER NOT NULL, " +
                        "`retry_count` INTEGER NOT NULL DEFAULT 0, " +
                        "`enqueued_at` TEXT NOT NULL, " +
                        "`next_retry_at` TEXT, " +
                        "`failed_at` TEXT, " +
                        "`last_error` TEXT" +
                        ")"
                )

                db.execSQL(
                    "INSERT INTO `ledger_outbox_events_new` (" +
                        "`event_id`, `action`, `payload`, `local_target_id`, `server_target_id`, " +
                        "`client_request_id`, `sequence`, `retry_count`, `enqueued_at`, `next_retry_at`, " +
                        "`failed_at`, `last_error`" +
                        ") " +
                        "SELECT " +
                        "`event_id`, `action`, `payload`, `local_target_id`, `server_target_id`, " +
                        "`event_id` AS `client_request_id`, " +
                        "`rowid` AS `sequence`, " +
                        "`retry_count`, " +
                        "`created_at` AS `enqueued_at`, " +
                        "NULL AS `next_retry_at`, " +
                        "CASE WHEN `failed_at` = 'null' THEN NULL ELSE `failed_at` END AS `failed_at`, " +
                        "`last_error` " +
                        "FROM `ledger_outbox_events`"
                )

                db.execSQL("DROP TABLE `ledger_outbox_events`")
                db.execSQL("ALTER TABLE `ledger_outbox_events_new` RENAME TO `ledger_outbox_events`")

                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ledger_outbox_events_client_request_id` ON `ledger_outbox_events` (`client_request_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_outbox_events_sequence` ON `ledger_outbox_events` (`sequence`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_outbox_events_local_target_id` ON `ledger_outbox_events` (`local_target_id`)")
            }
        }

        /** 1 → 3 跨版本兜底迁移（防御性） */
        val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 若从 1 直接升级，先执行 2->3
                MIGRATION_2_3.migrate(db)
            }
        }

        fun getInstance(context: Context): LedgerDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LedgerDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_1_3)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
