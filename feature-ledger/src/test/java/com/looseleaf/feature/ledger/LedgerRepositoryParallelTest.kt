package com.looseleaf.feature.ledger

import com.looseleaf.app.model.AssetsOverview
import com.looseleaf.app.model.CategoryOption
import com.looseleaf.app.model.CreditChannel
import com.looseleaf.feature.ledger.data.LedgerRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

/**
 * AND-12：账本首屏三个接口必须并行。
 *
 * 用三个各延迟 100ms 的 fake 数据源验证：
 * - 总耗时接近 100ms 而不是 300ms；
 * - 三个请求确实同时在途（并发峰值 = 3）；
 * - 任一失败不影响其余结果落库。
 */
class LedgerRepositoryParallelTest {

    @Before
    fun setUp() = LedgerRepository.clear()

    @After
    fun tearDown() = LedgerRepository.clear()

    private val categories = listOf(
        CategoryOption(scope = "expense", name = "餐饮"),
        CategoryOption(scope = "income", name = "薪资"),
        CategoryOption(scope = "payment", name = "微信支付")
    )

    @Test
    fun `三个首屏接口并行请求`() = runBlocking {
        val inFlight = AtomicInteger(0)
        val maxInFlight = AtomicInteger(0)
        suspend fun <T> tracked(value: T): T {
            val now = inFlight.incrementAndGet()
            maxInFlight.updateAndGet { maxOf(it, now) }
            delay(100)
            inFlight.decrementAndGet()
            return value
        }

        val elapsed = measureTimeMillis {
            LedgerRepository.refreshAll(
                fetchCategories = { tracked(categories) },
                fetchCreditChannels = { tracked(listOf(CreditChannel(code = "huabei", name = "花呗"))) },
                fetchAssetsOverview = { tracked(AssetsOverview(netWorth = 12345)) }
            )
        }

        assertTrue("并行后总耗时应显著小于串行的 300ms，实际 ${elapsed}ms", elapsed < 250)
        assertEquals("三个请求应同时在途", 3, maxInFlight.get())
        val snapshot = LedgerRepository.snapshot.value
        assertEquals(listOf("餐饮"), snapshot.expenseCategories)
        assertEquals(listOf("薪资"), snapshot.incomeCategories)
        assertEquals(listOf("微信支付"), snapshot.paymentMethods)
        assertEquals("花呗", snapshot.creditChannels.single().name)
        assertEquals(12345L, snapshot.assetsOverview?.netWorth)
    }

    @Test
    fun `单个接口失败不阻塞其余结果`() = runBlocking {
        LedgerRepository.refreshAll(
            fetchCategories = { throw IllegalStateException("分类接口挂了") },
            fetchCreditChannels = { listOf(CreditChannel(code = "huabei", name = "花呗")) },
            fetchAssetsOverview = { AssetsOverview(netWorth = 999) }
        )
        val snapshot = LedgerRepository.snapshot.value
        assertEquals(DefaultExpenseCategoriesForTest, snapshot.expenseCategories)
        assertEquals("花呗", snapshot.creditChannels.single().name)
        assertNotNull(snapshot.assetsOverview)
        assertEquals(999L, snapshot.assetsOverview?.netWorth)
    }

    private companion object {
        val DefaultExpenseCategoriesForTest = listOf("餐饮", "交通", "购物", "娱乐", "住房", "学习", "医疗", "其他")
    }
}
