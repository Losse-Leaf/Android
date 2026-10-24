package com.looseleaf.app

import com.looseleaf.app.data.sync.coalesceBursts
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AND-3：同步逐页写库产生的连续 revision 必须合并成少量刷新，
 * 且**最后一次**变更不能被去抖吞掉。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RevisionCoalescingTest {

    @Test
    fun `密集变更只刷新一次并保留最后值`() = runTest {
        val source = MutableSharedFlow<Long>(extraBufferCapacity = 64)
        val collected = mutableListOf<Long>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            source.coalesceBursts(debounceMs = 400).toList(collected)
        }

        repeat(20) { source.emit(it.toLong()) }
        advanceTimeBy(399)
        runCurrent()
        assertTrue("去抖窗口内不应刷新：$collected", collected.isEmpty())

        advanceTimeBy(2)
        runCurrent()
        assertEquals("应只收到最后一次变更", listOf(19L), collected)
    }

    @Test
    fun `两批相隔较远的变更各刷新一次`() = runTest {
        val source = MutableSharedFlow<Long>(extraBufferCapacity = 64)
        val collected = mutableListOf<Long>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            source.coalesceBursts(debounceMs = 400).toList(collected)
        }

        source.emit(1L)
        advanceTimeBy(401)
        runCurrent()
        source.emit(2L)
        advanceTimeBy(401)
        runCurrent()

        assertEquals(listOf(1L, 2L), collected)
    }
}
