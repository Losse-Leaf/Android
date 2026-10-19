package com.looseleaf.app

import com.looseleaf.app.updater.UpdateChecker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun testVersionComparison() {
        assertTrue(UpdateChecker.isVersionGreaterThan("1.0.1", "1.0.0"))
        assertTrue(UpdateChecker.isVersionGreaterThan("1.1.0", "1.0.0"))
        assertTrue(UpdateChecker.isVersionGreaterThan("2.0.0", "1.9.9"))
        assertTrue(UpdateChecker.isVersionGreaterThan("1.0.0.1", "1.0.0"))

        assertFalse(UpdateChecker.isVersionGreaterThan("1.0.0", "1.0.0"))
        assertFalse(UpdateChecker.isVersionGreaterThan("0.9.9", "1.0.0"))
        assertFalse(UpdateChecker.isVersionGreaterThan("1.0.0", "1.0.1"))
    }

    @Test
    fun testPrereleaseSuffixIgnored() {
        // 带后缀的 tag 不应整段退化成 0，否则永远检测不到更新
        assertTrue(UpdateChecker.isVersionGreaterThan("1.4.6-beta", "1.4.5"))
        assertFalse(UpdateChecker.isVersionGreaterThan("1.4.6-beta", "1.4.6"))
        assertTrue(UpdateChecker.isVersionGreaterThan("v2.0.0", "1.9.9"))
    }
}
