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

        /**
         * 2 → 6：跨越 v3/v4/v5 三个大版本的兜底迁移。
         *
         * 背景：v1.2.x 出厂的是 DB version 2，而 v1.2 时代 exportSchema=false、
         * 没有留下 schema 档案，1→2、2→3 的迁移在 git 历史里也从未存在。
         * 若不补这条路，停留在 v1.2.x 的安装直接升级到当前版本时，
         * Room 会抛 `A migration from 2 to 6 was required but not found` 并陷入启动崩溃循环。
         *
         * v2 是上古 beta schema（无 exportSchema、字段远少于现在），保留旧数据的代价
         * 远高于收益，因此这里直接清空六张表并按 6.json 的 DDL 重建到 v6。
         * 未同步的发件箱事件会一并丢失——这正是不升级就一直停留在旧版本、升级即重建的代价，
         * 总比整个应用不可用要好。
         */
        val MIGRATION_2_6 = object : Migration(2, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val tables = listOf(
                    "cached_entries",
                    "cached_transactions",
                    "outbox_events",
                    "cached_projects",
                    "cached_wishes",
                    "cached_footprints"
                )
                tables.forEach { db.execSQL("DROP TABLE IF EXISTS `$it`") }

                // 以下 DDL 与 app/schemas/.../6.json 逐字段一致；改动 schema 时必须同步更新这里
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cached_entries` (" +
                        "`local_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`server_id` INTEGER, `type` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`content` TEXT NOT NULL, `occurred_at` TEXT NOT NULL, `location` TEXT, " +
                        "`city_code` TEXT, `city_name` TEXT, `featured` INTEGER, `project_id` INTEGER, " +
                        "`wish_id` INTEGER, `footprint_id` INTEGER, `attachments_json` TEXT NOT NULL, " +
                        "`sync_status` TEXT NOT NULL, `created_at` TEXT NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_entries_occurred_at` ON `cached_entries` (`occurred_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_entries_server_id` ON `cached_entries` (`server_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_entries_sync_status` ON `cached_entries` (`sync_status`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cached_transactions` (" +
                        "`local_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `server_id` INTEGER, " +
                        "`amount` INTEGER NOT NULL, `type` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, `note` TEXT, `payment_method` TEXT, " +
                        "`occurred_at` TEXT NOT NULL, `city_code` TEXT, `wish_id` INTEGER, " +
                        "`footprint_id` INTEGER, `is_installment` INTEGER NOT NULL, " +
                        "`installment_channel_code` TEXT, `installment_total_amount` INTEGER, " +
                        "`installment_months` INTEGER, `installment_monthly_pay` INTEGER, " +
                        "`installment_remain_months` INTEGER, `repayment_day` INTEGER, " +
                        "`sync_status` TEXT NOT NULL, `created_at` TEXT NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_transactions_occurred_at` ON `cached_transactions` (`occurred_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_transactions_server_id` ON `cached_transactions` (`server_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_transactions_sync_status` ON `cached_transactions` (`sync_status`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `outbox_events` (" +
                        "`event_id` TEXT NOT NULL, `entity_type` TEXT NOT NULL, `action` TEXT NOT NULL, " +
                        "`payload` TEXT NOT NULL, `local_target_id` INTEGER NOT NULL, " +
                        "`server_target_id` INTEGER, `retry_count` INTEGER NOT NULL, " +
                        "`failed_at` TEXT, `last_error` TEXT, `created_at` TEXT NOT NULL, " +
                        "PRIMARY KEY(`event_id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_outbox_events_created_at` ON `outbox_events` (`created_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_outbox_events_entity_type` ON `outbox_events` (`entity_type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_outbox_events_failed_at` ON `outbox_events` (`failed_at`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cached_projects` (" +
                        "`id` INTEGER NOT NULL, `title` TEXT NOT NULL, `subtitle` TEXT, `description` TEXT, " +
                        "`story` TEXT, `category` TEXT, `status` TEXT, `github_url` TEXT, " +
                        "`featured` INTEGER, `order` INTEGER, `updated_at` TEXT, PRIMARY KEY(`id`))"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cached_wishes` (" +
                        "`id` INTEGER NOT NULL, `title` TEXT NOT NULL, `kind` TEXT NOT NULL, `note` TEXT, " +
                        "`spec` TEXT, `city_code` TEXT, `city_name` TEXT, `budget` INTEGER NOT NULL, " +
                        "`actual_cost` INTEGER NOT NULL, `resolution_type` TEXT, `actual_title` TEXT, " +
                        "`status` TEXT NOT NULL, `target_date` TEXT, `fulfilled_at` TEXT, `gear_id` TEXT, " +
                        "`attachments_json` TEXT NOT NULL, `updated_at` TEXT, PRIMARY KEY(`id`))"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cached_footprints` (" +
                        "`id` INTEGER NOT NULL, `city_code` TEXT NOT NULL, `city_name` TEXT NOT NULL, " +
                        "`title` TEXT, `visited_at` TEXT, `departed_at` TEXT, `note` TEXT, " +
                        "`life_entry_id` INTEGER, `wish_id` INTEGER, `attachments_json` TEXT NOT NULL, " +
                        "`entries_count` INTEGER, `total_spent` INTEGER, `transaction_count` INTEGER, " +
                        "`updated_at` TEXT, PRIMARY KEY(`id`))"
                )
            }
        }

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
                .addMigrations(MIGRATION_2_6, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
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
