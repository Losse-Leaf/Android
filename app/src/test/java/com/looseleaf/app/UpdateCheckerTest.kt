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
}
