package com.looseleaf.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedEntryEntity::class,
        CachedTransactionEntity::class,
        OutboxEventEntity::class,
        CachedProjectEntity::class,
        CachedWishEntity::class,
        CachedFootprintEntity::class
    ],
    version = 2,
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
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "looseleaf_local.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
