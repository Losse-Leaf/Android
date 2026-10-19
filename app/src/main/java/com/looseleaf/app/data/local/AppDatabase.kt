package com.looseleaf.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CachedEntryEntity::class,
        CachedTransactionEntity::class,
        OutboxEventEntity::class,
        CachedProjectEntity::class,
        CachedWishEntity::class,
        CachedFootprintEntity::class
    ],
    version = 6,
    // 导出 schema 到 app/schemas，后续迁移必须能和它逐字段对齐
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun outboxDao(): OutboxDao
    abstract fun projectDao(): ProjectDao
    abstract fun wishDao(): WishDao
    abstract fun footprintDao(): FootprintDao

    companion object {
        private const val DB_NAME = "looseleaf_local.db"

        @Volatile
        private var instance: AppDatabase? = null

        /** 只在列不存在时执行 ADD COLUMN，避免重复迁移直接崩库。 */
        private fun addColumnIfMissing(
            db: SupportSQLiteDatabase,
            table: String,
            column: String,
            ddl: String
        ) {
            val exists = db.query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                var found = false
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0 && cursor.getString(nameIndex) == column) {
                        found = true
                        break
                    }
                }
                found
            }
            if (!exists) db.execSQL(ddl)
        }

        /**
         * 3 → 4：移除 camera 列。
         * 不用 `ALTER TABLE DROP COLUMN`——它要求 SQLite ≥ 3.35（Android 12+），
         * 在 minSdk 26 的设备上必然失败，因此统一走「建新表 + 拷数据 + 换名」。
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 上次迁移若中途失败可能留下临时表，先清掉，避免重复拷数据
                db.execSQL("DROP TABLE IF EXISTS `cached_entries_new`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cached_entries_new` (" +
                        "`local_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`server_id` INTEGER, `type` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`content` TEXT NOT NULL, `occurred_at` TEXT NOT NULL, `location` TEXT, " +
                        "`city_code` TEXT, `city_name` TEXT, `attachments_json` TEXT NOT NULL, " +
                        "`sync_status` TEXT NOT NULL, `created_at` TEXT NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO `cached_entries_new` (`local_id`, `server_id`, `type`, `title`, " +
                        "`content`, `occurred_at`, `location`, `city_code`, `city_name`, " +
                        "`attachments_json`, `sync_status`, `created_at`) " +
                        "SELECT `local_id`, `server_id`, `type`, `title`, `content`, `occurred_at`, " +
                        "`location`, `city_code`, `city_name`, `attachments_json`, `sync_status`, " +
                        "`created_at` FROM `cached_entries`"
                )
                db.execSQL("DROP TABLE `cached_entries`")
                db.execSQL("ALTER TABLE `cached_entries_new` RENAME TO `cached_entries`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_entries_occurred_at` ON `cached_entries` (`occurred_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_entries_server_id` ON `cached_entries` (`server_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_entries_sync_status` ON `cached_entries` (`sync_status`)")
            }
        }

        /** 4 → 5：补齐造物 / 心愿 / 足迹与分期字段。异常不再吞掉，迁移失败必须显式暴露。 */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "cached_entries", "featured", "ALTER TABLE `cached_entries` ADD COLUMN `featured` INTEGER DEFAULT 0")
                addColumnIfMissing(db, "cached_entries", "project_id", "ALTER TABLE `cached_entries` ADD COLUMN `project_id` INTEGER")
                addColumnIfMissing(db, "cached_entries", "wish_id", "ALTER TABLE `cached_entries` ADD COLUMN `wish_id` INTEGER")
                addColumnIfMissing(db, "cached_entries", "footprint_id", "ALTER TABLE `cached_entries` ADD COLUMN `footprint_id` INTEGER")

                addColumnIfMissing(db, "cached_transactions", "is_installment", "ALTER TABLE `cached_transactions` ADD COLUMN `is_installment` INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "cached_transactions", "installment_channel_code", "ALTER TABLE `cached_transactions` ADD COLUMN `installment_channel_code` TEXT")
                addColumnIfMissing(db, "cached_transactions", "installment_total_amount", "ALTER TABLE `cached_transactions` ADD COLUMN `installment_total_amount` INTEGER")
                addColumnIfMissing(db, "cached_transactions", "installment_months", "ALTER TABLE `cached_transactions` ADD COLUMN `installment_months` INTEGER")
                addColumnIfMissing(db, "cached_transactions", "installment_monthly_pay", "ALTER TABLE `cached_transactions` ADD COLUMN `installment_monthly_pay` INTEGER")
                addColumnIfMissing(db, "cached_transactions", "installment_remain_months", "ALTER TABLE `cached_transactions` ADD COLUMN `installment_remain_months` INTEGER")
                addColumnIfMissing(db, "cached_transactions", "repayment_day", "ALTER TABLE `cached_transactions` ADD COLUMN `repayment_day` INTEGER")
                addColumnIfMissing(db, "cached_transactions", "wish_id", "ALTER TABLE `cached_transactions` ADD COLUMN `wish_id` INTEGER")
                addColumnIfMissing(db, "cached_transactions", "footprint_id", "ALTER TABLE `cached_transactions` ADD COLUMN `footprint_id` INTEGER")

                addColumnIfMissing(db, "cached_projects", "story", "ALTER TABLE `cached_projects` ADD COLUMN `story` TEXT")
                addColumnIfMissing(db, "cached_projects", "category", "ALTER TABLE `cached_projects` ADD COLUMN `category` TEXT")
                addColumnIfMissing(db, "cached_projects", "featured", "ALTER TABLE `cached_projects` ADD COLUMN `featured` INTEGER DEFAULT 0")

                addColumnIfMissing(db, "cached_wishes", "city_code", "ALTER TABLE `cached_wishes` ADD COLUMN `city_code` TEXT")
                addColumnIfMissing(db, "cached_wishes", "city_name", "ALTER TABLE `cached_wishes` ADD COLUMN `city_name` TEXT")
                addColumnIfMissing(db, "cached_wishes", "target_date", "ALTER TABLE `cached_wishes` ADD COLUMN `target_date` TEXT")
                addColumnIfMissing(db, "cached_wishes", "resolution_type", "ALTER TABLE `cached_wishes` ADD COLUMN `resolution_type` TEXT")
                addColumnIfMissing(db, "cached_wishes", "actual_title", "ALTER TABLE `cached_wishes` ADD COLUMN `actual_title` TEXT")
                addColumnIfMissing(db, "cached_wishes", "gear_id", "ALTER TABLE `cached_wishes` ADD COLUMN `gear_id` TEXT")
                addColumnIfMissing(db, "cached_wishes", "attachments_json", "ALTER TABLE `cached_wishes` ADD COLUMN `attachments_json` TEXT NOT NULL DEFAULT '[]'")

                addColumnIfMissing(db, "cached_footprints", "attachments_json", "ALTER TABLE `cached_footprints` ADD COLUMN `attachments_json` TEXT NOT NULL DEFAULT '[]'")
                addColumnIfMissing(db, "cached_footprints", "life_entry_id", "ALTER TABLE `cached_footprints` ADD COLUMN `life_entry_id` INTEGER")
                addColumnIfMissing(db, "cached_footprints", "wish_id", "ALTER TABLE `cached_footprints` ADD COLUMN `wish_id` INTEGER")
                addColumnIfMissing(db, "cached_footprints", "entries_count", "ALTER TABLE `cached_footprints` ADD COLUMN `entries_count` INTEGER")
                addColumnIfMissing(db, "cached_footprints", "total_spent", "ALTER TABLE `cached_footprints` ADD COLUMN `total_spent` INTEGER")
                addColumnIfMissing(db, "cached_footprints", "transaction_count", "ALTER TABLE `cached_footprints` ADD COLUMN `transaction_count` INTEGER")
            }
        }

        /** 5 → 6：发件箱增加失败态，事件不再被静默删除。 */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "outbox_events", "failed_at", "ALTER TABLE `outbox_events` ADD COLUMN `failed_at` TEXT")
                addColumnIfMissing(db, "outbox_events", "last_error", "ALTER TABLE `outbox_events` ADD COLUMN `last_error` TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_outbox_events_failed_at` ON `outbox_events` (`failed_at`)")
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                // 只在「降级安装」时清库；版本前进时缺迁移宁可显式失败，也不静默抹掉未同步数据。
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }
    }
}
