package com.looseleaf.app.data.local

import android.content.Context
import android.util.Log
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
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun outboxDao(): OutboxDao
    abstract fun projectDao(): ProjectDao
    abstract fun wishDao(): WishDao
    abstract fun footprintDao(): FootprintDao

    companion object {
        private const val TAG = "AppDatabase"
        private const val DB_NAME = "looseleaf_local.db"

        @Volatile
        private var instance: AppDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE `cached_entries` DROP COLUMN `camera`")
                } catch (_: Exception) {
                    try {
                        db.execSQL("CREATE TABLE IF NOT EXISTS `cached_entries_new` (`local_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `server_id` INTEGER, `type` TEXT NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `occurred_at` TEXT NOT NULL, `location` TEXT, `city_code` TEXT, `city_name` TEXT, `attachments_json` TEXT NOT NULL, `sync_status` TEXT NOT NULL, `created_at` TEXT NOT NULL)")
                        db.execSQL("INSERT INTO `cached_entries_new` (`local_id`, `server_id`, `type`, `title`, `content`, `occurred_at`, `location`, `city_code`, `city_name`, `attachments_json`, `sync_status`, `created_at`) SELECT `local_id`, `server_id`, `type`, `title`, `content`, `occurred_at`, `location`, `city_code`, `city_name`, `attachments_json`, `sync_status`, `created_at` FROM `cached_entries`")
                        db.execSQL("DROP TABLE `cached_entries`")
                        db.execSQL("ALTER TABLE `cached_entries_new` RENAME TO `cached_entries`")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_entries_occurred_at` ON `cached_entries` (`occurred_at`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_entries_server_id` ON `cached_entries` (`server_id`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_entries_sync_status` ON `cached_entries` (`sync_status`)")
                    } catch (e: Exception) {
                        Log.e(TAG, "MIGRATION_3_4 fallback: ${e.message}")
                    }
                }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            )
                .addMigrations(MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: try {
                    buildDatabase(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Database build error, recreating clean database: ${e.message}")
                    try {
                        context.applicationContext.deleteDatabase(DB_NAME)
                    } catch (_: Exception) {}
                    buildDatabase(context)
                }.also { instance = it }
            }
        }
    }
}
