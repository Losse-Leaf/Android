package com.looseleaf.app

import com.looseleaf.app.utils.FormatUtils
import com.looseleaf.app.utils.LocationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class FormatUtilsTest {

    @Test
    fun testCentsToYuanString() {
        assertEquals("¥42.50", FormatUtils.centsToYuanString(4250L))
        assertEquals("¥0.00", FormatUtils.centsToYuanString(0L))
        assertEquals("¥0.05", FormatUtils.centsToYuanString(5L))
        assertEquals("-¥12.80", FormatUtils.centsToYuanString(-1280L))
    }

    @Test
    fun testYuanStringToCents() {
        assertEquals(4250L, FormatUtils.yuanStringToCents("42.50"))
        assertEquals(3850L, FormatUtils.yuanStringToCents("38.5"))
        assertEquals(10000L, FormatUtils.yuanStringToCents("¥ 100"))
        assertEquals(0L, FormatUtils.yuanStringToCents(""))
        assertEquals(99L, FormatUtils.yuanStringToCents("0.99"))
    }

    @Test
    fun testParseNaturalTransactionInput() {
        val parsed = FormatUtils.parseNaturalTransactionInput("42.5 午餐手冲咖啡")
        assertEquals(4250L, parsed.amountCents)
        assertEquals("餐饮", parsed.category)
        assertEquals("午餐手冲咖啡", parsed.title)
        assertEquals("expense", parsed.type)

        val incomeParsed = FormatUtils.parseNaturalTransactionInput("+5000 工资发放")
        assertEquals(500000L, incomeParsed.amountCents)
        assertEquals("income", incomeParsed.type)
        assertEquals("工资收入", incomeParsed.category)

        val customIncomeParsed = FormatUtils.parseNaturalTransactionInput(
            input = "+5000 工资发放",
            knownCategories = listOf("吃喝", "杂项"),
            incomeCategories = listOf("月薪", "其他入账")
        )
        assertEquals("income", customIncomeParsed.type)
        assertEquals("月薪", customIncomeParsed.category)

        // 已选中「收入」标签时，无正负号的备注「500 兼职」必须维持 income，不得被悄悄改回 expense
        val selectedIncomeParsed = FormatUtils.parseNaturalTransactionInput(
            input = "500 兼职工资",
            knownCategories = listOf("餐饮", "其他"),
            incomeCategories = listOf("工资收入", "其他收入"),
            defaultType = "income"
        )
        assertEquals(50000L, selectedIncomeParsed.amountCents)
        assertEquals("income", selectedIncomeParsed.type)
        assertEquals("工资收入", selectedIncomeParsed.category)

        // 若备注显式写负号，即使 defaultType 为 income 也按支出解析
        val explicitMinusParsed = FormatUtils.parseNaturalTransactionInput(
            input = "-50 餐饮",
            knownCategories = listOf("餐饮", "其他"),
            incomeCategories = listOf("工资收入", "其他收入"),
            defaultType = "income"
        )
        assertEquals(5000L, explicitMinusParsed.amountCents)
        assertEquals("expense", explicitMinusParsed.type)
        assertEquals("餐饮", explicitMinusParsed.category)
    }

    @Test
    fun testInferCategoryRespectsTypeAndKnownDictionary() {
        // 支出文案包含「红包」或「利息」绝不能误判成收入分类「薪资」
        assertEquals(
            "其他",
            FormatUtils.inferCategoryFromText(
                text = "支付宝 付款 36.50 元（含红包抵扣 2.00 元）",
                knownCategories = listOf("餐饮", "交通", "购物", "其他"),
                type = "expense"
            )
        )
        assertEquals(
            "利息手续费",
            FormatUtils.inferCategoryFromText(
                text = "信用卡分期利息扣款 18.00 元",
                knownCategories = listOf("餐饮", "利息手续费", "其他"),
                type = "expense"
            )
        )
        // 用户删改默认分类后，返回值必须保证落在 knownCategories 中
        assertEquals(
            "杂项",
            FormatUtils.inferCategoryFromText(
                text = "支付宝 付款 36.50 元（含红包抵扣）",
                knownCategories = listOf("杂项", "生活"),
                type = "expense"
            )
        )
        // 收入文案严格在收入字典中匹配
        assertEquals(
            "礼金红包",
            FormatUtils.inferCategoryFromText(
                text = "微信收款 收到红包 88.00 元",
                knownCategories = listOf("工资收入", "礼金红包", "其他收入"),
                type = "income"
            )
        )
    }

    @Test
    fun testResolveQuickRecordCents() {
        // 手输优先：备注解析出的金额不能覆盖用户明确敲下的数字
        assertEquals(3000L, FormatUtils.resolveQuickRecordCents(3000L, 4250L))
        // 手输为空时采纳语义解析 —— 正是「备注写 42.5 午餐，点记下自动采纳」的场景
        assertEquals(4250L, FormatUtils.resolveQuickRecordCents(0L, 4250L))
        // 两者都空 / 解析无效：不可提交
        assertEquals(0L, FormatUtils.resolveQuickRecordCents(0L, null))
        assertEquals(0L, FormatUtils.resolveQuickRecordCents(0L, 0L))
        assertEquals(0L, FormatUtils.resolveQuickRecordCents(0L, -1L))
        // 手输有效、无解析结果：照常提交
        assertEquals(1280L, FormatUtils.resolveQuickRecordCents(1280L, null))
    }

    @Test
    fun testCalculateDaysBetween() {
        val iso1 = "2026-10-01T23:00:00+08:00"
        val iso2 = "2026-10-16T08:00:00+08:00"
        val days = FormatUtils.calculateDaysBetween(iso1, iso2)
        assertEquals(15L, days)

        // 纯日期格式解析与格式化测试
        assertEquals("2026.10.09", FormatUtils.formatDisplayDate("2026-10-09"))
        assertEquals("2026.10.09", FormatUtils.formatDisplayDate("2026.10.09"))
        assertEquals("2026.10.09 14:30", FormatUtils.formatDisplayDateTime("2026-10-09 14:30:00"))
    }

    @Test
    fun testParseExifDateTimeOrNull() {
        // 标准 EXIF 冒号日期格式必须能精确解析（此前缺少 yyyy:MM:dd HH:mm:ss 导致总是静默回退到当前时间）
        assertEquals(
            LocalDateTime.of(2026, 11, 20, 15, 30, 42),
            FormatUtils.parseExifDateTimeOrNull("2026:11:20 15:30:42")
        )
        // 带 EXIF 2.31 异地时区偏移（如东京 +09:00）时换算为北京时间 (+08:00)
        assertEquals(
            LocalDateTime.of(2026, 11, 20, 15, 30, 42),
            FormatUtils.parseExifDateTimeOrNull("2026:11:20 16:30:42", "+09:00")
        )
        // UTC 偏移 Z 正确换算为北京时间
        assertEquals(
            LocalDateTime.of(2026, 11, 20, 15, 30, 42),
            FormatUtils.parseExifDateTimeOrNull("2026:11:20 07:30:42", "Z")
        )
        // 相机未设时钟时的零值脏数据与空串必须返回 null
        assertNull(FormatUtils.parseExifDateTimeOrNull("0000:00:00 00:00:00"))
        assertNull(FormatUtils.parseExifDateTimeOrNull("   "))
        assertNull(FormatUtils.parseExifDateTimeOrNull(null))
        assertNotNull(FormatUtils.parseOccurredInstantOrNull("2026:11:20 15:30:42"))
    }

    @Test
    fun testLocationHelperValidationAndFormatting() {
        assertFalse(LocationHelper.isValidCoordinates(0.0, 0.0))
        assertFalse(LocationHelper.isValidCoordinates(Double.NaN, 120.0))
        assertFalse(LocationHelper.isValidCoordinates(95.0, 120.0))
        assertTrue(LocationHelper.isValidCoordinates(31.2304, 121.4737))

        assertEquals(
            "北京市 · 三里屯太古里",
            LocationHelper.formatLocationNameFromParts(
                adminArea = "北京市",
                locality = "北京市",
                subLocality = "朝阳区",
                thoroughfare = "三里屯路",
                featureName = "三里屯太古里"
            )
        )
        // 跳过纯门牌号 featureName，回退至道路名
        assertEquals(
            "上海市 · 南京西路",
            LocationHelper.formatLocationNameFromParts(
                adminArea = "上海市",
                locality = "上海市",
                subLocality = "静安区",
                thoroughfare = "南京西路",
                featureName = "1266号"
            )
        )
        // 跳过 Unnamed Road，回退至区县
        assertEquals(
            "杭州市 · 西湖区",
            LocationHelper.formatLocationNameFromParts(
                adminArea = "浙江省",
                locality = "杭州市",
                subLocality = "西湖区",
                thoroughfare = "Unnamed Road",
                featureName = "18"
            )
        )
    }

    @Test
    fun testParseNominatimLocationName() {
        val shanghaiRoad = """
            {
              "name": "",
              "address": {
                "road": "延安东路",
                "suburb": "南京东路街道",
                "city": "上海市",
                "city_district": "黄浦区",
                "state": "上海市"
              }
            }
        """.trimIndent()
        assertEquals("上海市 · 延安东路", LocationHelper.parseNominatimLocationName(shanghaiRoad))

        val beijingPoi = """
            {
              "name": "三里屯太古里",
              "address": {
                "road": "三里屯路",
                "district": "朝阳区",
                "city": "北京市",
                "state": "北京市"
              }
            }
        """.trimIndent()
        assertEquals("北京市 · 三里屯太古里", LocationHelper.parseNominatimLocationName(beijingPoi))

        val districtFallback = """
            {
              "name": "18",
              "address": {
                "road": "Unnamed Road",
                "district": "西湖区",
                "city": "杭州市",
                "province": "浙江省"
              }
            }
        """.trimIndent()
        assertEquals("杭州市 · 西湖区", LocationHelper.parseNominatimLocationName(districtFallback))

        assertEquals("", LocationHelper.parseNominatimLocationName("{not valid json"))
        assertEquals("", LocationHelper.parseNominatimLocationName(""))
    }
}
