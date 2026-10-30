package com.looseleaf.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.looseleaf.app.data.local.AppDatabase
import com.looseleaf.app.debug.DemoDataSeeder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DemoDataSeederTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        DemoDataSeeder.enabledForTesting = true
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        DemoDataSeeder.enabledForTesting = false
        database.close()
    }

    @Test
    fun testSeedAndClearDemoData() = runBlocking {
        assertFalse(DemoDataSeeder.isSeeded(context, database))

        val seededCount = DemoDataSeeder.seedDemoData(context, database)
        assertEquals(12, seededCount)
        assertTrue(DemoDataSeeder.isSeeded(context, database))

        val entries = database.entryDao().getAllEntries()
        assertEquals(12, entries.size)

        // Verify photo records exist
        val hasAttachments = entries.filter { it.attachmentsJson != "[]" }
        assertEquals(5, hasAttachments.size)

        // Verify pending and failed outbox events
        val pendingEvents = database.outboxDao().getAllPendingEvents()
        assertTrue(pendingEvents.any { it.eventId == "demo_outbox_pending_2" })

        val failedEvents = database.outboxDao().getFailedEvents()
        assertTrue(failedEvents.any { it.eventId == "demo_outbox_failed_5" })

        // Clear demo data
        val clearedCount = DemoDataSeeder.clearDemoData(context, database)
        println("Cleared count: $clearedCount")
        assertTrue("Cleared count should be > 0", clearedCount >= 12)

        val remainingEntries = database.entryDao().getAllEntries()
        println("Remaining entries count: ${remainingEntries.size}, items: ${remainingEntries.map { it.title }}")
        assertEquals(0, remainingEntries.size)

        assertFalse(DemoDataSeeder.isSeeded(context, database))
    }
}
