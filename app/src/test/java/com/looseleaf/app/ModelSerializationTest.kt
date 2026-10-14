package com.looseleaf.app

import com.looseleaf.app.model.LifeEntry
import com.looseleaf.app.model.Transaction
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testTransactionSerialization() {
        val tx = Transaction(
            id = 101,
            amount = 3850L,
            type = "expense",
            category = "餐饮",
            title = "麦当劳早餐",
            occurredAt = "2026-10-14T08:30:00+08:00"
        )

        val str = json.encodeToString(tx)
        val decoded = json.decodeFromString<Transaction>(str)

        assertEquals(tx.id, decoded.id)
        assertEquals(tx.amount, decoded.amount)
        assertEquals(tx.category, decoded.category)
        assertEquals(tx.title, decoded.title)
    }

    @Test
    fun testLifeEntrySerialization() {
        val entry = LifeEntry(
            id = 201,
            type = "travel",
            title = "大理行记",
            content = "苍山微雪，洱海无波。",
            occurredAt = "2026-10-12T16:00:00+08:00",
            location = "大理白族自治州"
        )

        val str = json.encodeToString(entry)
        val decoded = json.decodeFromString<LifeEntry>(str)

        assertEquals(entry.id, decoded.id)
        assertEquals(entry.type, decoded.type)
        assertEquals(entry.title, decoded.title)
    }
}
