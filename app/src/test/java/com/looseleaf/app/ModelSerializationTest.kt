package com.looseleaf.app

import com.looseleaf.app.model.CompleteUploadRequest
import com.looseleaf.app.model.DeleteResponse
import com.looseleaf.app.model.Footprint
import com.looseleaf.app.model.LifeEntry
import com.looseleaf.app.model.PresignUploadRequest
import com.looseleaf.app.model.Transaction
import com.looseleaf.app.model.WishesListResponse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun testPresignAndCompleteUploadContract() {
        val presign = PresignUploadRequest(
            filename = "shot.jpg",
            contentType = "image/jpeg",
            size = 102400L
        )
        val presignJson = json.encodeToString(presign)
        assertTrue(presignJson.contains("\"content_type\":\"image/jpeg\""))

        val complete = CompleteUploadRequest(
            uploadId = "up_12345678",
            objectKey = "entries/2026/10/shot.jpg",
            originalFilename = "shot.jpg",
            mimeType = "image/jpeg",
            size = 102400L,
            width = 1920,
            height = 1080
        )
        val completeJson = json.encodeToString(complete)
        assertTrue(completeJson.contains("\"upload_id\":\"up_12345678\""))
        assertTrue(completeJson.contains("\"original_filename\":\"shot.jpg\""))
    }

    @Test
    fun testWishesPaginatedEnvelopeAndFootprintAndDeleteResponse() {
        val wishesPayload = """{"total":1,"page":1,"page_size":50,"wishes":[{"id":7,"title":"Leica M6","kind":"item","budget":2500000,"actual_cost":0,"status":"wanting"}]}"""
        val wishesResp = json.decodeFromString<WishesListResponse>(wishesPayload)
        assertEquals(1, wishesResp.total)
        assertEquals("Leica M6", wishesResp.wishes.first().title)

        val footprintPayload = """{"id":3,"city_code":"532900","city_name":"大理白族自治州","title":"苍山漫游","visited_at":"2026-10-12T00:00:00+08:00","note":"洱海月色"}"""
        val fp = json.decodeFromString<Footprint>(footprintPayload)
        assertEquals("洱海月色", fp.note)
        assertEquals("苍山漫游", fp.title)

        val delNumeric = json.decodeFromString<DeleteResponse>("""{"id":12,"deleted":true}""")
        val delString = json.decodeFromString<DeleteResponse>("""{"id":"12","purged":true}""")
        assertEquals(true, delNumeric.deleted)
        assertEquals(true, delString.purged)
    }

    @Test
    fun testCategoriesResponseEnvelope() {
        val categoriesPayload = """{"items":[{"id":1,"scope":"expense","name":"餐饮","sort_order":0,"is_builtin":true},{"id":2,"scope":"income","name":"薪资","sort_order":1,"is_builtin":true}],"by_scope":{"expense":["餐饮"],"income":["薪资"]}}"""
        val resp = json.decodeFromString<com.looseleaf.app.model.CategoriesResponse>(categoriesPayload)
        assertEquals(2, resp.items.size)
        assertEquals("餐饮", resp.items.first().name)
        assertEquals(listOf("餐饮"), resp.byScope["expense"])
    }

    @Test
    fun testTransactionWithInstallmentsContract() {
        val tx = Transaction(
            id = 102,
            amount = 120000L,
            type = "expense",
            category = "数码器材",
            title = "iPad Pro M4",
            occurredAt = "2026-10-14T08:30:00+08:00",
            isInstallment = true,
            installmentChannelCode = "huabei",
            installmentTotalAmount = 120000L,
            installmentMonths = 12,
            installmentMonthlyPay = 10000L,
            installmentRemainMonths = 11,
            repaymentDay = 10
        )
        val str = json.encodeToString(tx)
        val decoded = json.decodeFromString<Transaction>(str)
        assertEquals(true, decoded.isInstallment)
        assertEquals("huabei", decoded.installmentChannelCode)
        assertEquals(12, decoded.installmentMonths)
        assertEquals(10, decoded.repaymentDay)
    }
}
