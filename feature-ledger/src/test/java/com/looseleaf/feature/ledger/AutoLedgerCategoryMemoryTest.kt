package com.looseleaf.feature.ledger

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.looseleaf.feature.ledger.data.CachedTransactionEntity
import com.looseleaf.feature.ledger.service.AutoLedgerCategoryMemory
import com.looseleaf.feature.ledger.service.AutoLedgerFeedbackNotifier
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutoLedgerCategoryMemoryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AutoLedgerCategoryMemory.clear(context)
    }

    @After
    fun tearDown() {
        AutoLedgerCategoryMemory.clear(context)
    }

    @Test
    fun `本地学习商户映射持久化且重启内存后仍命中`() {
        val learned = AutoLedgerCategoryMemory.recordLocalLearn(
            context = context,
            type = "expense",
            titleOrMerchant = "转账 · 楼下王叔面馆（万达店）",
            category = "餐饮"
        )
        assertNotNull(learned)
        assertEquals("楼下王叔面馆", learned!!.matchKey)

        // 模拟进程重启
        AutoLedgerCategoryMemory.resetInMemoryForTest()
        val map = AutoLedgerCategoryMemory.lookupMap(context, "expense")
        assertEquals("餐饮", map["楼下王叔面馆"])

        // 通用占位标题不得学入记忆
        assertNull(
            AutoLedgerCategoryMemory.recordLocalLearn(
                context = context,
                type = "expense",
                titleOrMerchant = "微信支付自动记账",
                category = "餐饮"
            )
        )
    }

    @Test
    fun `冷启动预热只吸收手动账目且隔离旧版自动记账脏数据`() {
        val history = listOf(
            CachedTransactionEntity(
                localId = 1L,
                amount = 2200L,
                type = "expense",
                category = "餐饮",
                title = "老王牛肉面",
                note = "午饭",
                occurredAt = "2026-11-20T12:00:00+08:00"
            ),
            CachedTransactionEntity(
                localId = 2L,
                amount = 1500L,
                type = "expense",
                category = "购物",
                title = "极速快剪",
                note = "自动记账 (微信支付 - 购物): 旧版误判",
                occurredAt = "2026-11-21T12:00:00+08:00"
            )
        )

        AutoLedgerCategoryMemory.warmupFromManualHistoryIfEmpty(context, history)
        val map = AutoLedgerCategoryMemory.lookupMap(context, "expense")
        assertEquals("餐饮", map["老王牛肉面"])
        assertTrue("旧自动记账脏数据不得进入冷启动记忆", !map.containsKey("极速快剪"))
    }

    @Test
    fun `快捷改类通知按钮排除当前分类与系统保留分类`() {
        val candidates = AutoLedgerFeedbackNotifier.pickQuickCategories(
            type = "expense",
            currentCategory = "其他",
            availableCategories = listOf("餐饮", "交通", "购物", "好物购置", "利息手续费", "其他")
        )
        assertEquals(listOf("餐饮", "交通", "购物"), candidates)
    }
}
