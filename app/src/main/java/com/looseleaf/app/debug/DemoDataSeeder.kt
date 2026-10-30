package com.looseleaf.app.debug

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.looseleaf.app.BuildConfig
import com.looseleaf.app.data.local.AppDatabase
import com.looseleaf.app.data.local.CachedEntryEntity
import com.looseleaf.app.data.local.OutboxEventEntity
import com.looseleaf.app.data.sync.SyncManager
import com.looseleaf.app.model.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

/**
 * 散页 Android 客户端 · Debug 专用演示数据注入器
 *
 * 仅在 debug 构建有效 (BuildConfig.DEBUG)，覆盖：
 * 1. 12 条经典生活手账记录（时间从新到旧）
 * 2. 多天、跨月（2026-10 -> 2026-09）、跨年（2026 -> 2025）
 * 3. 有图（横图、竖图、方图）与无图
 * 4. 长标题、短标题、空摘要、中英混排
 * 5. 同步状态：正常归档、待放行（PENDING_CREATE）、待重验（FAILED）
 * 6. 一键清除功能，彻底删除注入的演示记录、发件箱事件与生成的本地图片
 */
object DemoDataSeeder {

    @Volatile
    var enabledForTesting: Boolean = false

    val DEMO_SERVER_IDS = (-88801L downTo -88812L).toList()
    private const val PREFS_NAME = "looseleaf_debug_prefs"
    private const val KEY_SEEDED_LOCAL_IDS = "demo_seeded_local_ids"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    /**
     * 判断当前是否已注入演示数据
     */
    suspend fun isSeeded(context: Context, database: AppDatabase): Boolean = withContext(Dispatchers.IO) {
        if (!BuildConfig.DEBUG && !enabledForTesting) return@withContext false
        for (sid in DEMO_SERVER_IDS) {
            if (database.entryDao().getEntryByServerId(sid) != null) return@withContext true
        }
        false
    }

    /**
     * 灌入 12 条演示数据
     */
    suspend fun seedDemoData(context: Context, database: AppDatabase): Int = withContext(Dispatchers.IO) {
        if (!BuildConfig.DEBUG && !enabledForTesting) return@withContext 0

        // 1. 先安全清理旧的演示数据，确保幂等
        clearDemoData(context, database)

        val demoImagesDir = File(context.filesDir, "demo_images").apply { mkdirs() }

        // 2. 生成 5 份优雅手账质感纯色渐变位图 (离线生成，零外部网络)
        val imgAutumnWalk = createGradientImage(
            dir = demoImagesDir,
            fileName = "autumn_walk_vertical.jpg",
            width = 750,
            height = 1000,
            startColor = 0xFFE07A5F.toInt(), // 赤陶暖杏红
            endColor = 0xFFF2CC8F.toInt(),   // 银杏金黄
            label = "Autumn Walk · 银杏与烤栗子"
        )
        val imgLongjing = createGradientImage(
            dir = demoImagesDir,
            fileName = "longjing_horizontal.jpg",
            width = 1200,
            height = 800,
            startColor = 0xFF2D6A4F.toInt(), // 狮峰山茶绿
            endColor = 0xFF74C69D.toInt(),   // 浅抹茶清绿
            label = "龙井村 · 漫步茶山"
        )
        val imgPaperTexture = createGradientImage(
            dir = demoImagesDir,
            fileName = "paper_texture_square.jpg",
            width = 900,
            height = 900,
            startColor = 0xFF6C757D.toInt(), // 复古纸灰
            endColor = 0xFFADB5BD.toInt(),   // 棉纸微白
            label = "散页纸纹噪点样片 · 1:1"
        )
        val imgPingjiang = createGradientImage(
            dir = demoImagesDir,
            fileName = "pingjiang_rain_vertical.jpg",
            width = 750,
            height = 1000,
            startColor = 0xFF1D3557.toInt(), // 姑苏青瓦蓝
            endColor = 0xFF457B9D.toInt(),   // 雨中湖水青
            label = "苏州平江路 · 雨中青石板"
        )
        val imgLedgerSketch = createGradientImage(
            dir = demoImagesDir,
            fileName = "ledger_sketch_horizontal.jpg",
            width = 1200,
            height = 800,
            startColor = 0xFF8D5B4C.toInt(), // 牛皮纸深棕
            endColor = 0xFFDDA15E.toInt(),   // 复古票据浅黄
            label = "手账小票草图设计"
        )

        // 3. 构建 12 条生活记录数据模型
        val entriesToInsert = mutableListOf<CachedEntryEntity>()
        val seededLocalIds = mutableListOf<Long>()

        // 1. 2026-10-27 10:28 日常 位置:1600 (无图)
        val e1 = CachedEntryEntity(
            serverId = -88801L,
            type = "life",
            title = "安静的早晨",
            content = "醒得比闹钟早，窗外的雾还没散。煮了一壶茶，什么也没想，就坐着看水汽往上走。",
            occurredAt = "2026-10-27T10:28:00+08:00",
            location = "1600",
            syncStatus = "SYNCED",
            createdAt = "2026-10-27T10:28:00+08:00"
        )
        val id1 = database.entryDao().insert(e1)
        seededLocalIds.add(id1)

        // 2. 2026-10-27 08:05 心愿 (无图, 状态: 待放行)
        val e2 = CachedEntryEntity(
            serverId = -88802L,
            type = "wish",
            title = "想在年底前去一趟海边",
            content = "不为拍照，也不为打卡，只想在冬天的海边站一会儿。",
            occurredAt = "2026-10-27T08:05:00+08:00",
            syncStatus = "PENDING_CREATE",
            createdAt = "2026-10-27T08:05:00+08:00"
        )
        val id2 = database.entryDao().insert(e2)
        seededLocalIds.add(id2)
        // 写入发件箱待发事件 (failed_at 为空)
        database.outboxDao().insert(
            OutboxEventEntity(
                eventId = "demo_outbox_pending_2",
                entityType = "entry",
                action = "CREATE",
                payload = "{\"title\":\"想在年底前去一趟海边\"}",
                localTargetId = id2,
                failedAt = null,
                lastError = null,
                createdAt = "2026-10-27T08:05:00+08:00"
            )
        )

        // 3. 2026-10-26 21:27 日常 位置:1600 (有图: 竖图)
        val att3 = listOf(
            Attachment(
                id = -99903L,
                entryId = -88803L,
                objectKey = "demo_autumn_walk",
                originalFilename = "autumn_walk_vertical.jpg",
                mimeType = "image/jpeg",
                size = imgAutumnWalk.length(),
                width = 750,
                height = 1000,
                url = "file://${imgAutumnWalk.absolutePath}"
            )
        )
        val e3 = CachedEntryEntity(
            serverId = -88803L,
            type = "life",
            title = "AutumnWalk",
            content = "晚饭后绕着河走了一圈，银杏黄得很不真实。路灯亮起来的那一刻，叶子像被点着了。回来的路上买了一袋烤栗子，烫手，一路换着手拿。",
            occurredAt = "2026-10-26T21:27:00+08:00",
            location = "1600",
            attachmentsJson = json.encodeToString(att3),
            syncStatus = "SYNCED",
            createdAt = "2026-10-26T21:27:00+08:00"
        )
        val id3 = database.entryDao().insert(e3)
        seededLocalIds.add(id3)

        // 4. 2026-10-26 14:40 游历 位置:杭州·龙井村 (有图: 横图)
        val att4 = listOf(
            Attachment(
                id = -99904L,
                entryId = -88804L,
                objectKey = "demo_longjing",
                originalFilename = "longjing_horizontal.jpg",
                mimeType = "image/jpeg",
                size = imgLongjing.length(),
                width = 1200,
                height = 800,
                url = "file://${imgLongjing.absolutePath}"
            )
        )
        val e4 = CachedEntryEntity(
            serverId = -88804L,
            type = "travel",
            title = "龙井村的下午",
            content = "山路比想象中陡。茶农说今年雨水多，味道会淡一点。",
            occurredAt = "2026-10-26T14:40:00+08:00",
            location = "杭州·龙井村",
            cityName = "杭州市",
            attachmentsJson = json.encodeToString(att4),
            syncStatus = "SYNCED",
            createdAt = "2026-10-26T14:40:00+08:00"
        )
        val id4 = database.entryDao().insert(e4)
        seededLocalIds.add(id4)

        // 5. 2026-10-24 23:12 造物 (有图: 方图, 状态: 待重验)
        val att5 = listOf(
            Attachment(
                id = -99905L,
                entryId = -88805L,
                objectKey = "demo_paper_texture",
                originalFilename = "paper_texture_square.jpg",
                mimeType = "image/jpeg",
                size = imgPaperTexture.length(),
                width = 900,
                height = 900,
                url = "file://${imgPaperTexture.absolutePath}"
            )
        )
        val e5 = CachedEntryEntity(
            serverId = -88805L,
            type = "project",
            title = "给散页写了一个纸纹背景",
            content = "试了三种噪点方案，最后选了最淡的那个。太明显会抢文字，太淡又等于没有，折腾了一晚上才找到平衡。",
            occurredAt = "2026-10-24T23:12:00+08:00",
            attachmentsJson = json.encodeToString(att5),
            syncStatus = "FAILED",
            createdAt = "2026-10-24T23:12:00+08:00"
        )
        val id5 = database.entryDao().insert(e5)
        seededLocalIds.add(id5)
        // 写入发件箱失败待重验事件 (failed_at 非空，附带错误详情)
        database.outboxDao().insert(
            OutboxEventEntity(
                eventId = "demo_outbox_failed_5",
                entityType = "entry",
                action = "CREATE",
                payload = "{\"title\":\"给散页写了一个纸纹背景\"}",
                localTargetId = id5,
                failedAt = "2026-10-24T23:15:00+08:00",
                lastError = "服务端同步网络超时 (504 Gateway Timeout)",
                retryCount = 3,
                createdAt = "2026-10-24T23:12:00+08:00"
            )
        )

        // 6. 2026-10-23 18:06 日常 (长标题长摘要截断测试, 无图)
        val e6 = CachedEntryEntity(
            serverId = -88806L,
            type = "life",
            title = "这一条标题故意写得特别特别长，用来测试两行省略是否正常工作，看看会不会撑破布局",
            content = "正文故意写得非常长，至少超过六行，用于严格验证列表卡片的三行截断机制。\n这是第二行文字，记录一些琐碎的思绪与窗外的光景。\n这是第三行文字，按规范应该在此行末尾呈现省略号（…）截断。\n这是第四行文字，如果出现在列表卡片上，则说明三行截断机制没有正常工作。\n这是第五行多余内容，用于确保即使在宽屏或特大字体下也能超出三行限制。\n这是第六行最终校验文字，完整内容仅允许在点击展开的详情页面中阅读。",
            occurredAt = "2026-10-23T18:06:00+08:00",
            syncStatus = "SYNCED",
            createdAt = "2026-10-23T18:06:00+08:00"
        )
        val id6 = database.entryDao().insert(e6)
        seededLocalIds.add(id6)

        // 7. 2026-10-23 12:30 日常 (空摘要测试, 无图)
        val e7 = CachedEntryEntity(
            serverId = -88807L,
            type = "life",
            title = "午饭",
            content = "",
            occurredAt = "2026-10-23T12:30:00+08:00",
            syncStatus = "SYNCED",
            createdAt = "2026-10-23T12:30:00+08:00"
        )
        val id7 = database.entryDao().insert(e7)
        seededLocalIds.add(id7)

        // 8. 2026-10-19 16:20 游历 位置:苏州·平江路 (有图: 竖图)
        val att8 = listOf(
            Attachment(
                id = -99908L,
                entryId = -88808L,
                objectKey = "demo_pingjiang",
                originalFilename = "pingjiang_rain_vertical.jpg",
                mimeType = "image/jpeg",
                size = imgPingjiang.length(),
                width = 750,
                height = 1000,
                url = "file://${imgPingjiang.absolutePath}"
            )
        )
        val e8 = CachedEntryEntity(
            serverId = -88808L,
            type = "travel",
            title = "下雨天走平江路",
            content = "伞沿滴水，青石板亮得发黑。在一家小店躲雨，老板送了一碟糖藕。",
            occurredAt = "2026-10-19T16:20:00+08:00",
            location = "苏州·平江路",
            cityName = "苏州市",
            attachmentsJson = json.encodeToString(att8),
            syncStatus = "SYNCED",
            createdAt = "2026-10-19T16:20:00+08:00"
        )
        val id8 = database.entryDao().insert(e8)
        seededLocalIds.add(id8)

        // 9. 2026-10-12 09:15 造物 (有图: 横图)
        val att9 = listOf(
            Attachment(
                id = -99909L,
                entryId = -88809L,
                objectKey = "demo_ledger_sketch",
                originalFilename = "ledger_sketch_horizontal.jpg",
                mimeType = "image/jpeg",
                size = imgLedgerSketch.length(),
                width = 1200,
                height = 800,
                url = "file://${imgLedgerSketch.absolutePath}"
            )
        )
        val e9 = CachedEntryEntity(
            serverId = -88809L,
            type = "project",
            title = "账本页面草图",
            content = "纯文字记账太单调，想试试把每笔花销做成一张小票的样子。",
            occurredAt = "2026-10-12T09:15:00+08:00",
            attachmentsJson = json.encodeToString(att9),
            syncStatus = "SYNCED",
            createdAt = "2026-10-12T09:15:00+08:00"
        )
        val id9 = database.entryDao().insert(e9)
        seededLocalIds.add(id9)

        // 10. 2026-09-30 22:48 心愿 (跨月测试, 9月, 无图)
        val e10 = CachedEntryEntity(
            serverId = -88810L,
            type = "wish",
            title = "学会做一道拿手菜",
            content = "不用多，一道就够。朋友来了能端出来的那种。",
            occurredAt = "2026-09-30T22:48:00+08:00",
            syncStatus = "SYNCED",
            createdAt = "2026-09-30T22:48:00+08:00"
        )
        val id10 = database.entryDao().insert(e10)
        seededLocalIds.add(id10)

        // 11. 2026-09-28 07:50 日常 位置:1600 (英文中英混排, 无图)
        val e11 = CachedEntryEntity(
            serverId = -88811L,
            type = "life",
            title = "Lazy Sunday",
            content = "Slept in, burned the toast, no regrets.",
            occurredAt = "2026-09-28T07:50:00+08:00",
            location = "1600",
            syncStatus = "SYNCED",
            createdAt = "2026-09-28T07:50:00+08:00"
        )
        val id11 = database.entryDao().insert(e11)
        seededLocalIds.add(id11)

        // 12. 2025-12-31 23:59 日常 (跨年测试, 2025年, 无图)
        val e12 = CachedEntryEntity(
            serverId = -88812L,
            type = "life",
            title = "这一年",
            content = "回头看，大部分日子平平无奇，但正是这些平平无奇的日子，拼成了一年。",
            occurredAt = "2025-12-31T23:59:00+08:00",
            syncStatus = "SYNCED",
            createdAt = "2025-12-31T23:59:00+08:00"
        )
        val id12 = database.entryDao().insert(e12)
        seededLocalIds.add(id12)

        // 4. 持久化记录 localId 便于一键精准回滚
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_SEEDED_LOCAL_IDS, seededLocalIds.joinToString(",")).apply()

        // 5. 立即通知时间流 UI 刷新
        runCatching {
            SyncManager.getInstance(context).notifyLocalWrite()
        }

        seededLocalIds.size
    }

    /**
     * 一键彻底清除演示数据
     */
    suspend fun clearDemoData(context: Context, database: AppDatabase): Int = withContext(Dispatchers.IO) {
        if (!BuildConfig.DEBUG && !enabledForTesting) return@withContext 0

        var deletedCount = 0

        // 1. 按已知 server_id 清除
        for (sid in DEMO_SERVER_IDS) {
            val existed = database.entryDao().getEntryByServerId(sid)
            if (existed != null) {
                database.entryDao().deleteByServerId(sid)
                deletedCount++
            }
        }

        // 2. 清除旧测试数据（QuietMorning、"乐观散页-…"等残留数据）
        val remainingEntries = database.entryDao().getAllEntries()
        for (item in remainingEntries) {
            if (item.serverId == null || item.serverId !in DEMO_SERVER_IDS) {
                database.outboxDao().deleteByLocalTarget("entry", item.localId)
                database.entryDao().deleteByLocalId(item.localId)
                deletedCount++
            }
        }

        // 3. 按持久化的 localId 清除（双重兜底）
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val localIdsStr = sp.getString(KEY_SEEDED_LOCAL_IDS, null)
        if (!localIdsStr.isNullOrBlank()) {
            val localIds = localIdsStr.split(",").mapNotNull { it.toLongOrNull() }
            for (lid in localIds) {
                database.outboxDao().deleteByLocalTarget("entry", lid)
                val existed = database.entryDao().getEntryByLocalId(lid)
                if (existed != null) {
                    database.entryDao().deleteByLocalId(lid)
                    deletedCount++
                }
            }
        }
        sp.edit().remove(KEY_SEEDED_LOCAL_IDS).apply()

        // 3. 清理演示发件箱事件
        database.outboxDao().deleteById("demo_outbox_pending_2")
        database.outboxDao().deleteById("demo_outbox_failed_5")

        // 4. 清理本地生成的演示图片目录
        val demoImagesDir = File(context.filesDir, "demo_images")
        if (demoImagesDir.exists()) {
            demoImagesDir.deleteRecursively()
        }

        // 5. 立即通知时间流 UI 刷新
        runCatching {
            SyncManager.getInstance(context).notifyLocalWrite()
        }

        deletedCount
    }

    private fun createGradientImage(
        dir: File,
        fileName: String,
        width: Int,
        height: Int,
        startColor: Int,
        endColor: Int,
        label: String
    ): File {
        val file = File(dir, fileName)
        if (file.exists() && file.length() > 0) return file

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 优雅渐变底色
        val gradient = LinearGradient(
            0f, 0f, width.toFloat() * 0.8f, height.toFloat(),
            startColor, endColor,
            Shader.TileMode.CLAMP
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = gradient
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // 柔和微噪点纸纹感
        val noisePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x18FFFFFF
        }
        val step = 4f
        var y = 0f
        while (y < height) {
            var x = (y.toInt() % 3).toFloat()
            while (x < width) {
                canvas.drawPoint(x, y, noisePaint)
                x += step
            }
            y += step
        }

        // 照片中心手写感标签文字
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xAAFFFFFF.toInt()
            textSize = (minOf(width, height) * 0.055f).coerceAtLeast(26f)
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        canvas.drawText(label, width / 2f, height / 2f + 10f, textPaint)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }
        bitmap.recycle()

        return file
    }
}
