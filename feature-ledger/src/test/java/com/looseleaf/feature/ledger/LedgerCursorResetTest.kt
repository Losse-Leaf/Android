package com.looseleaf.feature.ledger

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.looseleaf.feature.ledger.data.LedgerRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P1 分页修复配套：旧版可能已把游标推进到漏拉点之后，
 * 升级后每个中枢必须强制一次全量补拉，且只能执行一次。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LedgerCursorResetTest {

    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = context.getSharedPreferences("looseleaf_ledger_sync_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        LedgerRepository.init(context)
    }

    @After
    fun tearDown() {
        prefs.edit().clear().commit()
        LedgerRepository.clear()
    }

    @Test
    fun `一次性游标重置只清空一次`() {
        LedgerRepository.saveCursor("2026-11-08T10:00:00Z")

        LedgerRepository.resetCursorForPaginationFixIfNeeded()

        assertNull(LedgerRepository.getStoredCursor())

        LedgerRepository.saveCursor("2026-11-08T11:00:00Z")
        LedgerRepository.resetCursorForPaginationFixIfNeeded()

        assertEquals("2026-11-08T11:00:00Z", LedgerRepository.getStoredCursor())
    }
}
