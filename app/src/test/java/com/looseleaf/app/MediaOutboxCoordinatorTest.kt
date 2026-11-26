package com.looseleaf.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.local.AppDatabase
import com.looseleaf.app.data.sync.MediaOutboxCoordinator
import com.looseleaf.app.data.sync.OfflineMediaEntryPayload
import com.looseleaf.app.data.sync.OfflineMediaFileItem
import com.looseleaf.app.model.Attachment
import com.looseleaf.app.model.CreateEntryRequest
import com.looseleaf.app.model.LifeEntry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MediaOutboxCoordinatorTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `多图上传部分失败后重试不得重复上传已成功图片`() = runBlocking {
        val uploadCalls = ConcurrentHashMap<String, Int>()
        var failSecondOnce = true

        val outboxDir = File(context.filesDir, "outbox_media").apply { mkdirs() }
        val file1 = File(outboxDir, "test_photo_1.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val file2 = File(outboxDir, "test_photo_2.jpg").apply { writeBytes(byteArrayOf(4, 5, 6)) }

        var capturedCreateReq: CreateEntryRequest? = null
        val coordinator = MediaOutboxCoordinator(
            context = context,
            database = db,
            apiClient = APIClient.getInstance(context),
            json = json,
            notifyLocalWrite = {},
            scheduleSync = {},
            itemUploader = { _, file ->
                val count = uploadCalls.merge(file.name, 1, Int::plus) ?: 1
                if (file.name == "test_photo_2.jpg" && failSecondOnce) {
                    failSecondOnce = false
                    throw IOException("simulated transient error on photo 2 (attempt $count)")
                }
                val id = if (file.name == "test_photo_1.jpg") 101L else 102L
                Attachment(
                    id = id,
                    objectKey = "entries/${file.name}",
                    originalFilename = file.name,
                    mimeType = "image/jpeg",
                    size = file.length(),
                    url = "https://example.com/${file.name}"
                )
            },
            entryCreator = { req ->
                capturedCreateReq = req
                LifeEntry(
                    id = 888L,
                    type = req.type,
                    title = req.title,
                    content = req.content,
                    occurredAt = req.occurredAt,
                    location = req.location,
                    attachments = (req.attachmentIds ?: emptyList()).map { id ->
                        Attachment(
                            id = id,
                            objectKey = "entries/$id.jpg",
                            originalFilename = "$id.jpg",
                            mimeType = "image/jpeg",
                            url = "https://example.com/$id.jpg"
                        )
                    }
                )
            }
        )

        val localId = coordinator.createMediaEntryOffline(
            localImageFilePath = file1.absolutePath,
            mimeType = "image/jpeg",
            width = 1000,
            height = 800,
            req = CreateEntryRequest(
                type = "life",
                title = "",
                content = "",
                occurredAt = "2026-11-26T12:00:00+08:00",
                location = "上海市 · 延安东路"
            ),
            additionalMediaFiles = listOf(
                OfflineMediaFileItem(
                    filePath = file2.absolutePath,
                    mimeType = "image/jpeg",
                    width = 1200,
                    height = 900
                )
            )
        )

        val firstEvent = db.outboxDao().findEventByTarget("media_entry", localId, "CREATE")!!

        // 第 1 次尝试：第 1 张上传成功，第 2 张失败抛错
        val firstFailed = runCatching { coordinator.replayOutboxEvent(firstEvent) }.isFailure
        assertTrue("第 1 次应因第 2 张失败而抛出异常", firstFailed)
        assertEquals(1, uploadCalls["test_photo_1.jpg"])
        assertEquals(1, uploadCalls["test_photo_2.jpg"])

        // 验证第 1 张上传结果已回写到 Outbox 载荷断点中
        val checkpointedEvent = db.outboxDao().findEventById(firstEvent.eventId)!!
        val checkpointedPayload = json.decodeFromString<OfflineMediaEntryPayload>(checkpointedEvent.payload)
        assertEquals(101L, checkpointedPayload.files[0].uploadedAttachment?.id)
        assertNull(checkpointedPayload.files[1].uploadedAttachment)

        // 第 2 次重试：第 1 张必须直接复用断点，只上传第 2 张
        coordinator.replayOutboxEvent(checkpointedEvent)

        assertEquals("第 1 张不得被重复上传", 1, uploadCalls["test_photo_1.jpg"])
        assertEquals("第 2 张在重试时只补充上传 1 次", 2, uploadCalls["test_photo_2.jpg"])
        assertEquals(listOf(101L, 102L), capturedCreateReq?.attachmentIds)
        assertEquals("", capturedCreateReq?.content)
        assertNull(db.outboxDao().findEventById(firstEvent.eventId))
    }
}
