package com.looseleaf.app

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.looseleaf.app.data.local.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 7 → 8 迁移的回归（移除「精选」列）
 *
 * 背景：后端已**彻底删除** `featured` 字段，客户端却还在维护这一列 ——
 * 「设为精选」的星标按钮点下去后端静默忽略、响应也不再返回，
 * **星标永远点不亮，且没有任何报错**。与其留一列永远为假的死数据，不如把列去掉。
 *
 * 与 3 → 4 同样的理由：**不能用 `ALTER TABLE DROP COLUMN`**（要求 SQLite ≥ 3.35，
 * Android 12+），minSdk 26 的设备上必然失败，因此走「建新表 + 拷数据 + 换名」。
 *
 * 本测试**刻意让 Room 自己去跑这次迁移**：Room 在迁移完成后会用
 * `app/schemas/.../8.json` 校验真实表结构，一旦手写的迁移 SQL 与实体定义对不上，
 * 会直接抛「Migration didn't properly handle …」——这正是要防的那种漂移。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FeaturedColumnMigrationTest {

    private val dbName = "featured_migration_test.db"
    private var dbFile: File? = null

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        dbFile = context.getDatabasePath(dbName)
        dbFile?.parentFile?.mkdirs()
        dbFile?.delete()
    }

    @After
    fun tearDown() {
        dbFile?.delete()
    }

    /** 建一个**完整的 v7 形态**数据库（DDL 与 app/schemas/.../7.json 逐字段一致） */
    private fun createV7Database(): SupportSQLiteOpenHelper {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val callback = object : SupportSQLiteOpenHelper.Callback(7) {
            override fun onCreate(db: SupportSQLiteDatabase) {
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
                    "CREATE INDEX IF NOT EXISTS `index_cached_entries_sync_status_occurred_at` " +
                        "ON `cached_entries` (`sync_status`, `occurred_at`)"
                )

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
                    "CREATE INDEX IF NOT EXISTS `index_outbox_events_entity_type_local_target_id_action` " +
                        "ON `outbox_events` (`entity_type`, `local_target_id`, `action`)"
                )

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

                // 迁移前写入的数据 —— 迁移后必须还在
                db.execSQL(
                    "INSERT INTO `cached_entries` (`local_id`,`server_id`,`type`,`title`,`content`," +
                        "`occurred_at`,`featured`,`attachments_json`,`sync_status`,`created_at`) VALUES " +
                        "(1, 11, 'life', '迁移前写入', '内容', '2026-01-01T00:00:00+08:00', 1, '[]', 'SYNCED', '2026-01-01T00:00:00+08:00')"
                )
                db.execSQL(
                    "INSERT INTO `cached_projects` (`id`,`title`,`featured`,`order`) VALUES (7, '迁移前的造物', 1, 3)"
                )
                db.execSQL(
                    "INSERT INTO `cached_transactions` (`local_id`,`amount`,`type`,`category`,`title`," +
                        "`occurred_at`,`is_installment`,`sync_status`,`created_at`) VALUES " +
                        "(1, 1200000, 'expense', '数码', '分期显示器', '2026-01-01T00:00:00+08:00', 1, 'SYNCED', '2026-01-01T00:00:00+08:00')"
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(callback)
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config)
    }

    @Test
    fun `v7 到 v8 的迁移移除精选列且不丢数据`() {
        val helper = createV7Database()
        // 打开一次让 SQLite 真正落盘并写入 user_version = 7
        helper.writableDatabase
        helper.close()

        val context = ApplicationProvider.getApplicationContext<Context>()
        // 用 Room 打开同一个文件：Room 会跑 MIGRATION_7_8，并在迁移后
        // 用 8.json 校验真实表结构 —— 结构对不上会直接抛异常。
        val room = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_7_8)
            .allowMainThreadQueries()
            .build()

        val sqlite = room.openHelper.writableDatabase

        // 1. 精选列已消失
        listOf("cached_entries", "cached_projects").forEach { table ->
            val columns = mutableListOf<String>()
            sqlite.query("PRAGMA table_info(`$table`)").use { c ->
                val idx = c.getColumnIndexOrThrow("name")
                while (c.moveToNext()) columns += c.getString(idx)
            }
            assertFalse(
                "$table 仍残留 featured 列，实际列：$columns",
                "featured" in columns
            )
        }

        // 2. 数据未丢
        sqlite.query("SELECT title FROM cached_entries WHERE local_id = 1").use { c ->
            assertTrue(c.moveToNext())
            assertEquals("迁移前写入", c.getString(0))
        }
        sqlite.query("SELECT title FROM cached_projects WHERE id = 7").use { c ->
            assertTrue(c.moveToNext())
            assertEquals("迁移前的造物", c.getString(0))
        }
        sqlite.query("SELECT title FROM cached_transactions WHERE local_id = 1").use { c ->
            assertTrue(c.moveToNext())
            assertEquals("分期显示器", c.getString(0))
        }

        // 3. 索引仍在
        val entryIndexes = mutableListOf<String>()
        sqlite.query("PRAGMA index_list('cached_entries')").use { c ->
            val idx = c.getColumnIndexOrThrow("name")
            while (c.moveToNext()) entryIndexes += c.getString(idx)
        }
        assertTrue(
            "迁移后丢失 cached_entries 复合索引：$entryIndexes",
            "index_cached_entries_sync_status_occurred_at" in entryIndexes
        )

        room.close()
    }

    @Test
    fun `v7 到 v8 的迁移可重复执行而不炸库`() {
        val helper = createV7Database()
        helper.writableDatabase
        helper.close()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val room = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_7_8)
            .allowMainThreadQueries()
            .build()
        val sqlite = room.openHelper.writableDatabase

        // 迁移后再手工跑一次：迁移脚本必须幂等（重复执行不得抛异常）
        AppDatabase.MIGRATION_7_8.migrate(sqlite)

        val columns = mutableListOf<String>()
        sqlite.query("PRAGMA table_info('cached_entries')").use { c ->
            val idx = c.getColumnIndexOrThrow("name")
            while (c.moveToNext()) columns += c.getString(idx)
        }
        assertFalse("重复迁移后不该重新长出 featured 列，实际：$columns", "featured" in columns)
        sqlite.query("SELECT title FROM cached_entries WHERE local_id = 1").use { c ->
            assertTrue(c.moveToNext())
            assertEquals("迁移前写入", c.getString(0))
        }

        room.close()
    }
}
