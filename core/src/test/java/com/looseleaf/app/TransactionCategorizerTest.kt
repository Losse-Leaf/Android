package com.looseleaf.app

import com.looseleaf.app.utils.CategorizationConfidence
import com.looseleaf.app.utils.CategorizationInput
import com.looseleaf.app.utils.TransactionCategorizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionCategorizerTest {

    private val fullExpenses = listOf(
        "餐饮", "交通", "购物", "居家", "订阅", "娱乐", "医疗", "学习", "其他"
    )

    private val fullIncomes = listOf(
        "工资", "副业", "理财", "退款", "其他"
    )

    @Test
    fun `彻底消除历史常见误判`() {
        // 1. 「收款商户」不得再污染为购物，理发造型归入购物
        val barber = TransactionCategorizer.categorize(
            CategorizationInput(
                type = "expense",
                merchant = "阿强造型工作室",
                rawText = "微信支付 收款商户：阿强造型工作室 付款 45.00 元",
                knownCategories = fullExpenses
            )
        )
        assertEquals("购物", barber.category)

        // 2. 模板里的「微信 / 腾讯」不得误判娱乐
        val genericWeChat = TransactionCategorizer.categorize(
            CategorizationInput(
                type = "expense",
                merchant = null,
                rawText = "微信支付凭证：腾讯财付通扣款 12.00 元",
                knownCategories = fullExpenses
            )
        )
        assertEquals("其他", genericWeChat.category)
        assertEquals(CategorizationConfidence.LOW, genericWeChat.confidence)

        // 3. 美团复合词不再被「美团」短路到餐饮
        val meituanDrug = TransactionCategorizer.categorize(
            CategorizationInput(
                type = "expense",
                merchant = "美团买药",
                rawText = "支付宝 你已向美团买药付款 39.90 元",
                knownCategories = fullExpenses
            )
        )
        assertEquals("医疗", meituanDrug.category)

        val meituanTaxi = TransactionCategorizer.categorize(
            CategorizationInput(
                type = "expense",
                merchant = "美团打车",
                rawText = "微信支付 向美团打车付款 24.50 元",
                knownCategories = fullExpenses
            )
        )
        assertEquals("交通", meituanTaxi.category)

        // 4. 订阅 & 居家(话费/水电) 精确归类
        val icloud = TransactionCategorizer.categorize(
            CategorizationInput(
                type = "expense",
                merchant = "Apple iCloud",
                rawText = "支付宝 自动扣款：iCloud 云存储 6.00 元",
                knownCategories = fullExpenses
            )
        )
        assertEquals("订阅", icloud.category)

        val mobile = TransactionCategorizer.categorize(
            CategorizationInput(
                type = "expense",
                merchant = "中国移动",
                rawText = "微信支付 向中国移动话费充值 100.00 元",
                knownCategories = fullExpenses
            )
        )
        assertEquals("居家", mobile.category)

        val grid = TransactionCategorizer.categorize(
            CategorizationInput(
                type = "expense",
                merchant = "国家电网",
                rawText = "支付宝 国家电网电费 120.00 元",
                knownCategories = fullExpenses
            )
        )
        assertEquals("居家", grid.category)

        // 5. 水果「红富士苹果」归入餐饮，不得因为带「苹果」误判为购物/数码
        val fruitApple = TransactionCategorizer.categorize(
            CategorizationInput(
                type = "expense",
                merchant = "百果园",
                rawText = "向百果园付款 红富士苹果 25.00 元",
                knownCategories = fullExpenses
            )
        )
        assertEquals("餐饮", fruitApple.category)
    }

    @Test
    fun `商户历史记忆与自定义关键词优先级最高`() {
        val learned = mapOf("楼下张记" to "餐饮")
        val customKeywords = mapOf("娱乐" to listOf("喵星人", "主子罐头"))

        val hitMemory = TransactionCategorizer.categorize(
            CategorizationInput(
                type = "expense",
                merchant = "楼下张记(万达店)",
                rawText = "向楼下张记(万达店)付款 18.00 元",
                knownCategories = fullExpenses,
                categoryKeywords = customKeywords,
                learnedMerchantRules = learned
            )
        )
        assertEquals("餐饮", hitMemory.category)
        assertEquals(CategorizationConfidence.HIGH, hitMemory.confidence)
        assertEquals("商户记忆", hitMemory.matchedReason)

        val hitKeyword = TransactionCategorizer.categorize(
            CategorizationInput(
                type = "expense",
                merchant = "喵星人小屋",
                rawText = "向喵星人小屋付款 98.00 元",
                knownCategories = fullExpenses,
                categoryKeywords = customKeywords
            )
        )
        assertEquals("娱乐", hitKeyword.category)
        assertEquals(CategorizationConfidence.HIGH, hitKeyword.confidence)
    }

    @Test
    fun `精简字典下优雅降级且转账安全兜底`() {
        val compactDict = listOf("餐饮", "交通", "购物", "住房", "娱乐", "学习", "医疗", "其他")

        assertEquals(
            "餐饮",
            TransactionCategorizer.categorize(
                CategorizationInput(type = "expense", merchant = "星巴克", rawText = "付款 36.50", knownCategories = compactDict)
            ).category
        )
        assertEquals(
            "住房",
            TransactionCategorizer.categorize(
                CategorizationInput(type = "expense", merchant = "国家电网", rawText = "电费 88.00", knownCategories = compactDict)
            ).category
        )
        assertEquals(
            "其他",
            TransactionCategorizer.categorize(
                CategorizationInput(type = "expense", merchant = "张三", rawText = "微信转账", isTransfer = true, knownCategories = fullExpenses)
            ).category
        )
        assertEquals(
            "其他",
            TransactionCategorizer.categorize(
                CategorizationInput(type = "income", merchant = "张三", rawText = "微信转账收款", isTransfer = true, knownCategories = fullIncomes)
            ).category
        )
    }

    @Test
    fun `商户记忆键规范化拦截通用占位标题`() {
        assertNull(TransactionCategorizer.normalizeMerchantKey("微信支付自动记账"))
        assertNull(TransactionCategorizer.normalizeMerchantKey("自动记账"))
        assertNull(TransactionCategorizer.normalizeMerchantKey("招行 · 消费"))
        assertEquals("星巴克" to "星巴克", TransactionCategorizer.normalizeMerchantKey("星巴克（国贸店）"))
        assertEquals("张三" to "张三", TransactionCategorizer.normalizeMerchantKey("转账 · 张三"))
    }
}
