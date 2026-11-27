package com.looseleaf.feature.ledger.service

import com.looseleaf.app.model.CreateTransactionRequest
import com.looseleaf.app.utils.FormatUtils
import java.time.LocalDateTime
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * 自动记账结构化解析引擎 (AutoLedgerParser)
 *
 * 借鉴开源项目 `huanghhcri/SmartLedger` (MIT License) 与 `AutoAccountingOrg/AutoAccounting`
 * 非 Root 多渠道规则架构，整合「通道信号守卫 -> 卡尾号与余额遮罩 -> 非成交/赠券拦截 ->
 * 分渠道精确模板匹配 -> 跨端渠道线索提取与分类推理」完整管线：
 * 1. 全量覆盖微信、支付宝、云闪付、抖音、京东/淘宝收银台、工/农/中/建/招/邮储/交行等银行 App 与短信（含 95599 农业银行入账/转存）；
 * 2. 先遮罩 `尾号XXXX` 与 `(XXXX)` 卡尾号、剥离括号红包抵扣附注与余额后缀，杜绝卡号或余额误抓为金额；
 * 3. 精准区分「本周消费总结/月度账单」与带有「本月…」文案的真实单笔付款通知，避免过杀误拦；
 * 4. 提取 `channelHint`（微信/支付宝/京东/抖音/云闪付）与 `notifyOrigin`（wallet/bank），供跨端精准去重配对。
 */
internal object AutoLedgerParser {

    const val ORIGIN_WALLET = "wallet"
    const val ORIGIN_BANK = "bank"

    private val SMS_PACKAGES = setOf(
        "com.google.android.apps.messaging",
        "com.android.mms",
        "com.android.mms.service",
        "com.android.messaging",
        "com.samsung.android.messaging",
        "com.xiaomi.mms",
        "com.miui.mms",
        "com.huawei.message",
        "com.huawei.ohos.mms",
        "com.hihonor.mms",
        "com.hihonor.message",
        "com.coloros.mms",
        "com.oplus.mms",
        "com.oplus.messaging",
        "com.oppo.mms",
        "com.realme.mms",
        "com.vivo.mms",
        "com.meizu.mms",
        "com.meizu.flyme.mms",
        "com.oneplus.mms",
        "com.lenovo.mms",
        "com.zte.mms",
        "com.sonymobile.conversations",
        "com.motorola.messaging"
    )

    // 来自 SmartLedger (MIT) + AutoRuleSubmit 全量主流银行/数字人民币包名及碎片匹配
    private val BANK_PACKAGE_HINTS = listOf(
        "icbc", "psbc", "chinapost", "ccb", "abchina", "bankabc", "com.abc.",
        "bocmbci", "bocsoft", "com.boc", "cmb.pb", "cmb.b2c", "cmbchina",
        "pingan", "spdb", "cmbc", "cebbank", "bankcomm", "bocomm", "cib",
        "citic", "ecitic", "hxb", "cgbchina", "bankofbeijing", "bosc",
        "bankofshanghai", "bosh", "webank", "mybank", "njcb", "nbcb",
        "hzbank", "jsbchina", "czbank", "srcb", "gzcb", "cqcb", "hrbb",
        "csii", "yitong", "dcep", "chinamworld", ".bank."
    )

    private val E_COMMERCE_PACKAGES = setOf(
        "com.ss.android.ugc.aweme",
        "com.ss.android.ugc.live",
        "com.jd.jrapp",
        "com.jingdong.app.mall",
        "com.jd.jdlite",
        "com.taobao.taobao",
        "com.tmall.wireless"
    )

    // 微信仅放行官方支付/收款/正式转账回执渠道，严格屏蔽普通好友/群聊里闲聊消费金额的消息
    private val WECHAT_PAYMENT_CHANNEL_KEYWORDS = listOf(
        "微信支付", "微信收款助手", "微信收款商业版", "服务通知", "微信零钱", "零钱通",
        "支付凭证", "扣费凭证", "转账凭证", "收款到账", "转账到账", "转账成功", "转账收款",
        "转账通知", "收款通知", "零钱入账", "零钱支出", "对方已收款", "朋友已收款",
        "好友已收款", "已存入零钱", "面对面收款", "赞赏码", "AA收款", "群收款"
    )

    private val SMS_FINANCIAL_KEYWORDS = listOf(
        "银行", "农业", "农行", "工商", "工行", "建设", "建行", "招商", "招行",
        "中国银行", "中行", "邮储", "邮政", "交通银行", "交行", "浦发", "民生",
        "光大", "兴业", "平安", "中信", "华夏", "广发", "支付宝", "微信支付",
        "微信转账", "财付通", "银联", "云闪付", "网联", "信用卡", "储蓄卡",
        "借记卡", "尾号", "动账", "转存", "农信", "信用社"
    )

    private val BANK_SHORT_SENDERS = listOf(
        "95599", "95588", "95533", "95566", "95555", "95580", "95528",
        "95568", "95595", "95561", "95511", "95558", "95577", "95508",
        "95559", "95564", "95574"
    )

    private val BANK_IDENTIFIERS = listOf(
        "农业银行" to "农业银行", "农行" to "农业银行",
        "工商银行" to "工商银行", "工行" to "工商银行",
        "建设银行" to "建设银行", "建行" to "建设银行",
        "中国银行" to "中国银行", "中行" to "中国银行",
        "招商银行" to "招商银行", "招行" to "招商银行",
        "邮政储蓄" to "邮政储蓄", "邮储" to "邮政储蓄",
        "交通银行" to "交通银行", "交行" to "交通银行",
        "浦发银行" to "浦发银行", "浦发" to "浦发银行",
        "民生银行" to "民生银行", "光大银行" to "光大银行",
        "兴业银行" to "兴业银行", "平安银行" to "平安银行",
        "中信银行" to "中信银行", "华夏银行" to "华夏银行",
        "广发银行" to "广发银行", "数字人民币" to "数字人民币"
    )

    // 剥离微信通知前导未读计数（如「[2条]微信支付: ...」）
    private val LEADING_UNREAD_BADGE_REGEX = Regex("""^\[\d+条\]\s*""")

    // 遮罩卡号尾号，防止「尾号4321」「储蓄卡(7619)」被任何金额正则误吃
    private val CARD_TAIL_REGEX = Regex("""尾号\*{0,4}\d{4}""")
    private val CARD_PAREN_TAIL_REGEX = Regex("""(?<=储蓄卡|信用卡|借记卡|银行卡|账户)\s*[(（]\d{4}[)）]""")
    private val CARD_BRACKET_TAIL_REGEX = Regex("""【([^】]*?)[(（]\d{4}[)）]】""")

    // 剥离真实支付回执里括号中的抵扣/立减/红包附注（如「付款36.50元（含红包抵扣2.00元）」）
    private val PAREN_DISCOUNT_CLAUSE_REGEX = Regex(
        """[（(]\s*(?:含|已用|使用|其中|已|本次)?[^（）()]{0,28}(?:红包|立减|优惠|抵扣|积分|满减)[^（）()]{0,20}[）)]"""
    )

    // 剥离真实银行动账短信末尾带数字的余额/可用额度后缀（不剥离「余额不足」）
    private val TRAILING_BALANCE_CLAUSE_REGEX = Regex(
        """[，,。；;]?\s*(?:当前|账户|活期|卡内|剩余|可用|交易后)?(?:余额|可用额度|信用额度)\s*(?:为|人民币|RMB|CNY|¥|￥|:|：|\s)*[0-9,]+(?:\.[0-9]{1,2})?\s*元?"""
    )

    // 无论原文还是清洗后，只要出现即立刻拦截的硬性非成交关键词（精确短语，避免裸「本周/本月」误伤真实单笔付款）
    private val HARD_RAW_BLOCK_KEYWORDS = listOf(
        // 验证码与垃圾退订
        "验证码", "校验码", "动态码", "动态密码", "确认码", "安全码", "退订", "回复td", "回t",
        // 周期总结、汇总与分析报告（保留精确短语，不再裸封「上周/本周/上月/本月」）
        "上周消费", "上周支出", "上周账单", "上周总结", "上周共", "上周汇报",
        "本周消费", "本周支出", "本周账单", "本周总结", "本周共", "本周报告",
        "上月消费", "上月支出", "上月账单", "上月总结", "上月共", "上月报告",
        "本月消费总结", "本月支出总结", "本月账单", "本月总结", "本月总支出",
        "本月总消费", "本月累计消费", "本月累计支出", "本月累计收入", "本月共支出", "本月共消费", "本月应还",
        "上季度", "本季度",
        "年度账单", "年度总结", "年度报告", "年度消费", "年度支出", "年终总结", "年终账单",
        "近7天", "近30天", "近一周", "近一月", "最近一周", "最近一月", "过去一周", "过去一月",
        "昨日消费", "昨日支出", "昨日账单", "今日汇总", "今日消费汇总",
        "总结", "汇总", "周报", "月报", "年报", "季报", "报告",
        "周账单", "月账单", "年账单", "账单总结", "账单汇总", "账单报告", "账单小助手",
        "账单助手", "账单分析", "收支分析", "消费分析", "消费概况", "收支概况",
        "总支出", "总消费", "总收入", "共支出", "共消费", "共收入", "共计支出", "共计消费",
        "共计收入", "共计", "累计支出", "累计消费", "累计收入", "合计支出", "合计消费", "合计收入",
        "总计支出", "总计消费", "总计收入", "较上周", "比上周", "较上月", "比上月", "环比", "同比",
        "排行榜", "账单已出", "已出账", "本期账单", "出账通知", "出账提醒", "账单日",
        "花呗账单", "白条账单", "信用卡账单", "电子账单",
        // 还款提醒、信用分、贷款营销
        "本期应还", "待还款", "待还金额", "还款提醒", "到期提醒", "即将到期",
        "最后还款日", "最低还款", "预约还款", "自动还款提醒", "还款日", "逾期提醒",
        "额度调整", "额度提升", "授信额度", "提额", "临时额度", "贷款额度", "借款额度", "预估可借",
        "年化利率", "立即申请", "点击申请", "信用分", "芝麻信用", "芝麻分", "信用守护",
        // 收益日报与失败/待付/取消/状态通知
        "收益日报", "昨日收益", "七日年化", "万份收益", "预期收益", "基金估值", "行情提醒",
        "涨跌幅", "付款失败", "支付失败", "扣款失败", "交易失败", "转账失败", "收款失败",
        "交易关闭", "订单已取消", "已取消订单", "余额不足", "密码错误", "待支付", "待付款",
        "即将自动扣款", "扣款预告", "续费提醒", "即将扣费",
        "登录验证", "安全锁", "网银登录", "手机银行登录", "开户成功", "绑卡成功", "签约成功",
        "物流", "已发货", "待收货",
        // 本人账户内部流转（提现/充值/余额宝零钱通互转不属于实际消费或收入）
        "零钱提现", "提现到账", "提现成功", "余额提现", "转入余额宝", "转入零钱通",
        "零钱通转入", "余额宝转入", "零钱充值", "余额充值", "本人账户互转", "提现提醒"
    )

    // 针对清洗后正文拦截：营销、平台赠送红包、优惠券、体验金、互动小游戏等
    private val PROMO_AND_VOUCHER_KEYWORDS = listOf(
        "送你", "送您", "赠送", "赠您", "恭喜", "获得", "已发放", "发放了", "派发", "到账啦",
        "待领取", "立即领取", "点击领取", "免费领", "领红包", "扫码领", "天天领", "每天领", "最高领",
        "红包待领", "红包即将过期", "红包快过期", "红包雨", "瓜分红包", "拆红包", "开红包",
        "红包已", "个红包", "元红包", "红包金额", "无门槛", "抵扣金",
        "付款红包", "支付红包", "消费红包", "实体店红包", "线下红包", "通用红包", "到店红包",
        "现金红包", "抵扣红包", "立减红包", "随机红包", "惊喜红包", "专属红包", "专享红包",
        "体验金", "鼓励金", "奖励金", "立减金", "消费金", "福利金", "返现红包",
        "优惠券", "代金券", "消费券", "折扣券", "立减券", "满减券", "打车券", "话费券",
        "充值券", "通用券", "神券", "专享券", "满减", "卡包", "外卖券", "省钱金额", "已省回", "省回",
        "去使用", "立即使用", "马上用", "去核销", "有效期", "即将过期", "到期作废",
        "今日可用", "今天可用", "明日可用", "下单返", "待使用", "领券",
        "支付有礼", "消费有礼", "付款有礼", "有礼", "福利", "抽奖", "签到",
        "限时免费", "限时领取", "限时抢", "活动通知", "邀请好友", "商品降价", "降价提醒",
        "免费开通", "邀你开通", "会员到期提醒", "会员即将到期", "积分到期", "积分兑换", "积分到账",
        "蚂蚁森林", "蚂蚁庄园", "芭芭农场", "集五福", "理财推荐", "精选基金",
        "专属福利", "限时优惠", "尊享礼遇", "干饭", "请你吃"
    )

    private val AGGREGATE_COUNT_PATTERN = Regex(
        """(?:共|累计|合计|总计)\s*\d+\s*笔|\d+\s*笔(?:消费|支出|交易|账单|收入|付款|收款|转账)"""
    )

    // 拦截「满10减2」「1.50元红包」「红包 ¥2.00」「3元优惠券」等任意平台赠券/赠红包表达
    private val PROMO_AMOUNT_PATTERNS = listOf(
        Regex("""满\s*\d+(?:\.\d+)?\s*元?\s*(?:减|立减|可用|抵)"""),
        Regex("""\d+(?:\.\d{1,2})?\s*元?\s*(?:的)?(?:[\u4e00-\u9fa5]{0,4})?(?:红包|优惠券|代金券|消费券|立减券|满减券|立减金|体验金|抵扣金|折扣券|通用券|充值券)"""),
        Regex("""(?:红包|优惠券|代金券|消费券|立减券|满减券|立减金|体验金|抵扣金)\s*[:：为]?\s*[¥￥]?\s*\d+(?:\.\d{1,2})?""")
    )

    // 仅带「账面余额提醒」而无真实消费/入账动词的通知拦截
    private val PURE_BALANCE_REMINDER_REGEX = Regex("""^(?:.*)(?:当前余额|可用余额|账户余额)[^，。！？\n]*$""")

    // 真实单笔成交回执末尾附带的激励/抵扣/引导尾巴（仅在已确认单笔成交信号后剥离）
    private val TRAILING_REWARD_FOOTER_REGEX = Regex(
        """[，,。；;\s]+(?:含|已用|使用|本次|其中|另)?[^，。；;\n]{0,20}(?:红包抵扣|立减金|优惠券|满减|立减|红包|抵扣)[^，。；;\n]{0,24}$"""
    )
    private val TRAILING_PROMO_GUIDE_REGEX = Regex(
        """[，,。；;\s]+(?:获得|可获|已获|点击查看|点击|查看|支付有礼|消费有礼|付款有礼|蚂蚁森林|积分|能量)[^，。；;\n]{0,36}$"""
    )
    private val HARD_VOUCHER_GIFT_KEYWORDS = listOf(
        "送你", "送您", "赠送", "待领取", "立即领取", "点击领取", "免费领", "领红包",
        "体验金", "鼓励金", "立减金", "奖励金", "抵扣金", "福利金",
        "优惠券", "代金券", "消费券", "立减券", "满减券", "通用券", "充值券", "打车券",
        "干饭", "请你吃"
    )
    private val RECEIPT_CUE_WITH_AMOUNT_PATTERN = Pattern.compile(
        """(?:已支付|付款成功|成功付款|支付成功|扣款成功|扣费成功|自动扣款|免密支付|消费成功|转账成功|支付宝到账|微信收款|收款到账|退款到账|退款成功|入账成功|已入账|已到账)[^，。！？；\n]{0,28}?(?:[¥￥]|人民币|RMB|CNY)?\s*[0-9,]+(?:\.[0-9]{1,2})?\s*[元块]?"""
    )
    private val NON_RECEIPT_VOUCHER_NOUNS = listOf(
        "红包", "券", "积分", "体验金", "鼓励金", "立减金", "奖励金", "抵扣金", "福利金", "送你", "送您", "赠送"
    )

    private val WHITESPACE_REGEX = Regex("""\s+""")

    // ── 白名单金额与安全连接词 ──
    private const val AMOUNT_NUM = """[0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?"""

    // 严格金额段：
    // 1) 带明确货币符号/币种前缀 (¥12 / RMB 88.50 / 人民币36.50元)
    // 2) 带「元/块」单位 (36.50元 / 100 元)
    // 3) 显式小数且不跟日期/笔数/折扣/百分比单位 (完成转存交易人民币5000.00 / 消费 58.00)
    private const val STRICT_AMOUNT =
        """(?:(?:¥|￥|人民币|RMB|CNY)\s*[+-]?\s*($AMOUNT_NUM)(?![\d.])\s*[元块]?(?![万亿折%％])|[+-]?\s*($AMOUNT_NUM)(?![\d.])\s*[元块]|([0-9]{1,3}(?:,[0-9]{3})*\.[0-9]{1,2}|[0-9]+\.[0-9]{1,2})(?![\d.]|\s*[月日年时分秒笔次个张件期折%％万亿]))"""

    // 白名单安全连接词：动作词与金额之间仅允许空白/冒号/逗号+「金额」/「了|成功|支出」/括号渠道标/币种前缀
    private const val SAFE_GAP =
        """(?:[\s:：]*(?:了|成功|支出|入账)?|[\s,，:：]+(?:转账|付款|支付|收款|交易)?金额)?[\s:：]*(?:\([^()¥￥元]{1,32}\)|（[^（）¥￥元]{1,32}）)?[\s:：]*(?:交易|人民币|RMB|CNY|¥|￥)*\s*"""

    // 0. 明确“对方已收款”等转出回执（必须在收入规则之前判定，防止「已收款」把自己的转账支出误判为收入）
    private val OUTGOING_TRANSFER_ACCEPTED_PATTERN = Pattern.compile(
        """(?:对方|朋友|好友|收款人|收款方)已(?:收款|收钱|领取)(?:[\s,，:：]*(?:转账|付款|支付)?金额)?$SAFE_GAP$STRICT_AMOUNT"""
    )

    // 1. 明确收入模板（带付款人：「收到张三的转账50.00元」「张三给你转账100元」）
    private val INCOME_FROM_RECEIVED_PATTERN = Pattern.compile(
        """(?:已收到|收到)\s*(?:来自)?\s*([^，。！？；：\n¥￥]{1,24}?)\s*的?(?:微信转账|转账|汇款|打款|收款|退款)$SAFE_GAP$STRICT_AMOUNT"""
    )

    private val INCOME_FROM_SENT_TO_ME_PATTERN = Pattern.compile(
        """(?:^|[\s:：，,])([^，。！？；：\n¥￥\s]{1,24}?)\s*(?:已向你|已向您|已给你|已给您|向你|向您|给你|给您)\s*(?:转账|转入|汇款|付款)$SAFE_GAP$STRICT_AMOUNT"""
    )

    // 1b. 明确收入直出模板（含农行「完成转存交易人民币5000.00」「完成工资交易人民币...」「他行来账」「银联代付」与支付宝到账/花呗退款）
    private val INCOME_DIRECT_PATTERN = Pattern.compile(
        """(?:完成(?:转存|代发工资|工资|代发|银联入账|跨行转入|他行来账|行内来账|跨行来账|他行转入|网银转入|掌银转入|转账转入|网联来账|来账|汇入|现存|银联代付|退汇|退款|结息|利息|报销|收款|收入|入账|存入)交易|支付宝到账|支付宝收款|微信收款|微信转账到账|好友转账到账|转账到账|转账收款|转账退回|转账已退还|退还到账|已退还|零钱入账|收款成功|收款到账|扫码收款|二维码收款|面对面收款|收到转账|向你转账|向您转账|给你转账|给您转账|退款到账|收到退款|已退款|退款成功|已入账|入账成功|已到账|到账成功|已收钱|你已收款|您已收款|(?<!对方)(?<!朋友)(?<!好友)(?<!收款人)(?<!收款方)已收款|工资入账|代发工资|他行来账|行内来账|跨行来账|他行转入|银联代付|网联来账|转存|银联入账|转入|入账|存入|现存|汇入|来账|代发|到账|收款(?![方人账商码])|收入)$SAFE_GAP$STRICT_AMOUNT"""
    )

    // 银行通用的「完成<类型>交易人民币[+-]金额」模板（兼容农行 95599 全量交易类型）
    private val BANK_COMPLETED_TX_PATTERN = Pattern.compile(
        """完成([^，。！？；：\n\d]{1,18}?)交易\s*(?:人民币|RMB|CNY|¥|￥)?\s*([+-]?)\s*($AMOUNT_NUM)(?![\d.])\s*[元块]?"""
    )

    private val BANK_COMPLETED_INCOME_HINTS = listOf(
        "转存", "转入", "来账", "汇入", "入账", "存入", "现存", "工资", "代发",
        "代付", "退货", "退款", "退汇", "结息", "利息", "报销", "补贴", "奖金",
        "分红", "收入", "收款", "提现"
    )
    private val BANK_COMPLETED_EXPENSE_HINTS = listOf(
        "财付通", "支付宝", "微信", "快捷", "网上", "消费", "支出", "支取",
        "扣款", "扣费", "代扣", "缴费", "取款", "POS", "ATM", "刷卡", "转账"
    )

    private val INCOME_REVERSE_PATTERN = Pattern.compile(
        """(?:一笔|收到|完成一笔)\s*$STRICT_AMOUNT\s*的?(?:收款|收入|转入|入账|存入|转账|退款)"""
    )

    // 2. 明确支出模板：带商户/收款人（允许收款人/好友昵称含数字、字母与括号）
    private val EXPENSE_PAYEE_VERB_AFTER_PATTERN = Pattern.compile(
        """(?:你已向|您已向|已向|向|给)\s*([^，。！？；：\n¥￥]{1,24}?)\s*(?:付款|支付|转账|转出|消费)$SAFE_GAP$STRICT_AMOUNT"""
    )

    private val EXPENSE_PAYEE_VERB_BEFORE_PATTERN = Pattern.compile(
        """(?:付款给|支付给|转账给|转给|转出给)\s*([^，。！？；：\n¥￥]{1,24}?)(?:\s+|(?=[¥￥])|(?<=[^\d.]))(?:(?:转账|付款)?金额[\s:：]*)?$STRICT_AMOUNT"""
    )

    // 支付宝/银行「自动扣款：已从你的XX卡扣款28.00元」「已从XX账户扣除15.00元」
    private val EXPENSE_FROM_ACCOUNT_PATTERN = Pattern.compile(
        """(?:已从|从)\s*[^，。！？；：\n]{1,36}?\s*(?:扣款|扣费|扣除|支取|付款|支付)$SAFE_GAP$STRICT_AMOUNT"""
    )

    private val EXPENSE_REVERSE_PATTERN = Pattern.compile(
        """(?:你有一笔|您有一笔|一笔|成功)\s*$STRICT_AMOUNT\s*的?(?:支出|消费|付款|扣款|扣费|转账|转出|汇款)"""
    )

    // 3. 明确支出直出模板（含农行「完成财付通/支付宝/快捷支付/消费/支取交易」、工行「支出(...)」、京东「使用【...】支付」、支付宝/微信各种付款文案）
    private val EXPENSE_DIRECT_PATTERN = Pattern.compile(
        """(?:完成(?:财付通|支付宝|快捷支付|网上支付|银联消费|银联扣款|POS消费|刷卡消费|消费|支出|支取|转账|跨行转账|网银转账|掌银转账|扣款|扣费|缴费|取款)交易|使用【[^】]{1,40}】支付|(?:支付成功|付款成功|成功付款|扣款成功|扣费成功|消费成功|转账成功|扫码付款|扫码支付|免密支付|自动扣款|花呗消费|花呗付款|花呗支付|余额宝支付|亲情卡消费|快捷支付|刷卡消费|网上支付|POS消费|微信转账(?!到账|收款|退回|已退还|失败)|转账支出|转账付款|跨行转账|网银转账|手机银行转账|零钱支付|零钱支出|已转账|实付|已付|已支付|(?<!微信)支付(?!宝)|付款|消费|扣款|扣费|支出|支取|扣除|转出|(?<!收到)(?<!向你)(?<!向您)(?<!给你)(?<!给您)转账(?!到账|收款|退回|已退还|失败|金额为0))$SAFE_GAP|微信支付[\s:：]{1,2}|支付宝[\s:：]{1,2}[¥￥]?)$STRICT_AMOUNT"""
    )

    // 3b. 银行正负号动账模板（如农行/建行/招行「完成财付通交易人民币-50.00」「变动金额：-120.50元」「入账+200.00元」）
    private val SIGNED_EXPENSE_PATTERN = Pattern.compile(
        """(?:交易|变动|支出|扣款|转账|消费|财付通|支付宝|微信转账|金额)[^，。！？；\n\d+]{0,16}-\s*($AMOUNT_NUM)(?![\d.])\s*[元块]?"""
    )
    private val SIGNED_INCOME_PATTERN = Pattern.compile(
        """(?:交易|变动|收入|入账|转入|转存|转账|收款|到账|金额)[^，。！？；\n\d-]{0,16}\+\s*($AMOUNT_NUM)(?![\d.])\s*[元块]?"""
    )

    // 4. 受控兜底：仅当通知带明确成交凭证词且出现「金额/¥」字段时启用
    private val INCOME_CUE_PATTERN = Pattern.compile(
        """收款成功|到账成功|收款到账|转账到账|转账收款|转账退回|转账已退还|已收钱|(?<!对方)(?<!朋友)(?<!好友)(?<!收款人)(?<!收款方)已收款|已存入零钱|收款通知|退款成功|退款到账|零钱入账|转存交易|代发工资|银联入账|入账成功|他行来账|行内来账|跨行来账|他行转入|银联代付|网联来账|现存|汇入|来账|代发"""
    )
    private val EXPENSE_CUE_PATTERN = Pattern.compile(
        """支付成功|付款成功|成功付款|扣款成功|扣费成功|消费成功|交易成功|转账成功|已支付|已付款|已扣款|已转账|支付凭证|扣费凭证|转账凭证|转账通知|对方已收款|朋友已收款|好友已收款|动账通知|动账提醒|零钱支出|微信转账|免密支付|自动扣款|财付通交易"""
    )
    private val CUE_AMOUNT_PATTERN = Pattern.compile(
        """(?:[¥￥]|(?:付款|支付|交易|扣款|收款|转账|变动|实付|汇款|入账)?金额[\s:：¥￥人民币RMB CNY]{0,8})\s*-?\s*($AMOUNT_NUM)(?![\d.])\s*[元块]?(?![万亿折%％])"""
    )

    // 商户/收款对象抽取正则（按精准度排序）
    private val MERCHANT_PATTERNS = listOf(
        Pattern.compile("""(?:你已向|您已向|已向|向)\s*([^，。！？；：\n¥￥]{1,24}?)\s*(?:付款|支付|转账|消费)"""),
        Pattern.compile("""(?:付款给|支付给|转账给|转给)\s*([^，。！？；：\n¥￥]{1,24}?)(?:[\s，。！？；：¥￥]|$)"""),
        Pattern.compile("""(?:收款方|收款商户|交易商户|消费商户|对方户名|收款人|商户|商家|交易对方|付款方)\s*[:：]\s*([^，。！？；：\n¥￥]{1,24})"""),
        Pattern.compile("""(?:财付通|支付宝|微信支付|美团支付|抖音支付|网联|云闪付)[-—]\s*([^，。！？；：\n()（）¥￥\d]{2,20}?)(?:支出|消费|付款|支付|扣款|扣费|转出|转入|人民币|RMB|CNY|[，。！？；：\n()（）¥￥\d]|$)"""),
        Pattern.compile("""(?:来自)\s*([^，。！？；：\n¥￥]{2,20}?)\s*(?:的)?(?:转账|汇款|收款|退款)"""),
        Pattern.compile("""(?:在)\s*([^，。！？；：\n¥￥]{2,18}?)\s*(?:快捷支付|刷卡消费|消费|付款|支付)""")
    )

    // 支付中介/通道词不算具体商户名，避免跨平台去重时把「财付通」和「星巴克」误判为两家不同商户
    private val NON_MERCHANT_LABELS = setOf(
        "商户", "商家", "平台", "平台商户", "网银在线", "理财网银在线", "银行", "余额", "账户",
        "微信", "微信支付", "微信转账", "支付宝", "财付通", "银联", "云闪付", "网联",
        "花呗", "余额宝", "零钱", "零钱通", "储蓄卡", "信用卡", "抖音支付", "京东支付",
        "服务通知", "微信收款助手", "微信收款商业版", "通知", "收款方"
    )

    fun classifyPackage(pkg: String): NotificationChannelGroup? {
        if (pkg.isBlank()) return null
        val lower = pkg.lowercase(Locale.ROOT)
        return when {
            pkg == "com.tencent.mm" -> NotificationChannelGroup.WECHAT
            pkg == "com.eg.android.AlipayGphone" -> NotificationChannelGroup.ALIPAY
            pkg == "com.unionpay" -> NotificationChannelGroup.UNIONPAY
            pkg in SMS_PACKAGES -> NotificationChannelGroup.BANK_SMS
            pkg in E_COMMERCE_PACKAGES -> NotificationChannelGroup.E_COMMERCE
            BANK_PACKAGE_HINTS.any { lower.contains(it) } -> NotificationChannelGroup.BANK_APP
            else -> null
        }
    }

    fun isAllowedPackage(pkg: String): Boolean = classifyPackage(pkg) != null

    /** 判断短信广播是否为银行或支付动账短信（参考 SmartLedger SmsReceiver） */
    fun isFinancialSms(sender: String, body: String): Boolean {
        if (body.isBlank()) return false
        val hasFinancialIdentity = SMS_FINANCIAL_KEYWORDS.any { body.contains(it) } ||
            BANK_SHORT_SENDERS.any { sender.contains(it) }
        if (!hasFinancialIdentity) return false
        val hasCurrencyOrAmount = body.contains("元") ||
            body.contains("￥") ||
            body.contains("¥") ||
            body.contains("人民币") ||
            body.contains("RMB", ignoreCase = true) ||
            body.contains("CNY", ignoreCase = true) ||
            body.contains("金额") ||
            SMS_DECIMAL_AMOUNT_REGEX.containsMatchIn(body)
        return hasCurrencyOrAmount
    }

    private val SMS_DECIMAL_AMOUNT_REGEX = Regex("""\d+\.\d{2}""")

    internal fun isWeChatAllowedChannel(sanitized: String): Boolean {
        if (WECHAT_PAYMENT_CHANNEL_KEYWORDS.any { sanitized.contains(it) }) return true
        val bankLabel = identifyBankLabel(sanitized)
        return hasBankLedgerSignal(sanitized) ||
            (bankLabel != null && BANK_WECHAT_ALERT_KEYWORDS.any { sanitized.contains(it) })
    }

    fun hasBankLedgerSignal(text: String): Boolean {
        if (text.contains("动账")) return true
        if (text.contains("支出(") || text.contains("收入(") || text.contains("消费(")) return true
        if (text.contains("交易金额") || text.contains("支出人民币") || text.contains("收入人民币") || text.contains("存入人民币")) return true
        if (text.contains("完成") && text.contains("交易") && text.contains("人民币")) return true
        if (Regex("""尾号\*{0,4}\d{0,4}.*(?:支出|收入|消费|扣款|转入|转存|入账|存入|到账)""").containsMatchIn(text)) return true
        return false
    }

    fun looksLikeUnlistedBankOrSmsNotification(text: String): Boolean {
        if (hasBankLedgerSignal(text)) return true
        val hasBankOrSender = identifyBankLabel(text) != null ||
            text.contains("银行") || text.contains("储蓄卡") || text.contains("信用卡") ||
            text.contains("借记卡") || text.contains("尾号")
        if (!hasBankOrSender || !hasPayableAmountSignal(text)) return false
        return UNLISTED_BANK_ACTION_KEYWORDS.any { text.contains(it) }
    }

    private val UNLISTED_BANK_ACTION_KEYWORDS = listOf(
        "支出", "消费", "扣款", "付款", "转出", "收入", "入账", "到账", "存入", "转存", "转入", "退款", "交易提醒", "动账"
    )

    private val BANK_WECHAT_ALERT_KEYWORDS = listOf(
        "交易提醒", "动账", "支出", "收入", "转存", "入账", "扣款", "消费", "到账", "存入"
    )

    fun hasPayableAmountSignal(text: String): Boolean =
        text.contains("元") || text.contains("￥") || text.contains("¥") || text.contains("人民币")

    fun looksLikeGroupedSummary(text: String): Boolean {
        if (Regex("""\[\d+\s*条\]""").containsMatchIn(text)) return true
        if (Regex("""\d+\s*条新消息""").containsMatchIn(text)) return true
        return false
    }

    fun looksLikeLedgerChild(text: String): Boolean {
        if (text.isBlank()) return false
        return hasBankLedgerSignal(text) ||
            text.contains("已支付") || text.contains("付款") || text.contains("支出") ||
            text.contains("消费") || text.contains("扣款") || text.contains("收款") ||
            text.contains("到账") || text.contains("入账") || text.contains("转存") ||
            text.contains("退款") || text.contains("转账")
    }

    fun maskCardTailNumbers(text: String): String {
        var masked = CARD_TAIL_REGEX.replace(text, "尾号****")
        masked = CARD_PAREN_TAIL_REGEX.replace(masked, "")
        masked = CARD_BRACKET_TAIL_REGEX.replace(masked, "【$1】")
        return masked
    }

    fun stripAuxiliaryClauses(raw: String): String {
        var cleaned = LEADING_UNREAD_BADGE_REGEX.replace(raw.trim(), "")
        cleaned = maskCardTailNumbers(cleaned)
        cleaned = PAREN_DISCOUNT_CLAUSE_REGEX.replace(cleaned, " ")
        cleaned = TRAILING_BALANCE_CLAUSE_REGEX.replace(cleaned, "")
        return cleaned.replace(WHITESPACE_REGEX, " ").trim()
    }

    fun stripTrailingRewardOrPromoFooter(text: String): String {
        var result = text.trim()
        repeat(3) {
            val updated = TRAILING_REWARD_FOOTER_REGEX.replace(result, "").let {
                TRAILING_PROMO_GUIDE_REGEX.replace(it, "")
            }.trim()
            if (updated == result) return result
            result = updated
        }
        return result
    }

    fun hasStrongPaymentReceiptSignal(sanitized: String): Boolean {
        if (sanitized.isBlank()) return false
        val coreCandidate = stripTrailingRewardOrPromoFooter(sanitized)
        if (coreCandidate.isBlank()) return false

        val matchedCoreSlice: String? = listOf(
            OUTGOING_TRANSFER_ACCEPTED_PATTERN,
            INCOME_FROM_RECEIVED_PATTERN,
            INCOME_FROM_SENT_TO_ME_PATTERN,
            EXPENSE_PAYEE_VERB_AFTER_PATTERN,
            EXPENSE_PAYEE_VERB_BEFORE_PATTERN,
            EXPENSE_FROM_ACCOUNT_PATTERN,
            EXPENSE_REVERSE_PATTERN,
            INCOME_REVERSE_PATTERN,
            RECEIPT_CUE_WITH_AMOUNT_PATTERN
        ).firstNotNullOfOrNull { pattern ->
            val m = pattern.matcher(coreCandidate)
            if (m.find()) m.group(0) else null
        }
        if (matchedCoreSlice != null && NON_RECEIPT_VOUCHER_NOUNS.none { matchedCoreSlice.contains(it) }) {
            return true
        }
        return hasBankLedgerSignal(coreCandidate)
    }

    fun isVerificationCodeOrSpam(content: String): Boolean {
        if (content.isBlank()) return true
        val rawLower = content.lowercase(Locale.ROOT)
        if (HARD_RAW_BLOCK_KEYWORDS.any { rawLower.contains(it) }) return true
        if (AGGREGATE_COUNT_PATTERN.containsMatchIn(content)) return true

        val sanitized = stripAuxiliaryClauses(content)
        if (sanitized.isBlank()) return true

        // 明确银行动账信号已通过硬性拦截（验证码/失败/余额不足/汇总）后，不被尾随推广词误杀
        if (hasBankLedgerSignal(content)) {
            return false
        }

        // 明确单笔成交回执（如「向瑞幸咖啡付款18.00元，获得蚂蚁森林能量」）：
        // 先剥离末尾奖励/引导附注，仅检查主体是否仍为赠券/营销消息
        if (hasStrongPaymentReceiptSignal(sanitized)) {
            val core = stripTrailingRewardOrPromoFooter(sanitized)
            if (core.isBlank()) return true
            val coreLower = core.lowercase(Locale.ROOT)
            if (HARD_VOUCHER_GIFT_KEYWORDS.any { coreLower.contains(it) }) return true
            if (PROMO_AMOUNT_PATTERNS.any { it.containsMatchIn(core) }) return true
            return false
        }

        val sanitizedLower = sanitized.lowercase(Locale.ROOT)
        if (PROMO_AND_VOUCHER_KEYWORDS.any { sanitizedLower.contains(it) }) return true
        if (PROMO_AMOUNT_PATTERNS.any { it.containsMatchIn(sanitized) }) return true
        if (PURE_BALANCE_REMINDER_REGEX.matches(sanitized)) return true

        return false
    }

    fun normalizeContentForDedupe(content: String): String =
        content.trim().lowercase(Locale.ROOT).replace(WHITESPACE_REGEX, " ")

    fun parseYuanStringToCents(str: String?): Long? {
        if (str.isNullOrBlank()) return null
        return FormatUtils.yuanStringToCents(str).takeIf { it > 0L }
    }

    fun parse(
        content: String,
        pkg: String = "",
        expenseCategories: List<String> = emptyList(),
        incomeCategories: List<String> = emptyList()
    ): ParsedAutoLedgerBill? {
        if (isVerificationCodeOrSpam(content)) return null

        val channelGroup = classifyPackage(pkg) ?: inferChannelFromContent(content)
        val rawSanitized = stripAuxiliaryClauses(content)
        if (rawSanitized.isBlank()) return null
        val sanitized = if (hasStrongPaymentReceiptSignal(rawSanitized)) {
            stripTrailingRewardOrPromoFooter(rawSanitized)
        } else {
            rawSanitized
        }
        if (sanitized.isBlank()) return null

        val bankLabel = identifyBankLabel(sanitized)
        val isWeChatBankOfficialAlert = channelGroup == NotificationChannelGroup.WECHAT &&
            (hasBankLedgerSignal(sanitized) ||
                (bankLabel != null && BANK_WECHAT_ALERT_KEYWORDS.any { sanitized.contains(it) }))

        // 渠道前置准入守卫
        when (channelGroup) {
            NotificationChannelGroup.BANK_SMS -> {
                if (SMS_FINANCIAL_KEYWORDS.none { sanitized.contains(it) } &&
                    !hasBankLedgerSignal(sanitized)
                ) {
                    return null
                }
            }
            NotificationChannelGroup.WECHAT -> {
                if (WECHAT_PAYMENT_CHANNEL_KEYWORDS.none { sanitized.contains(it) } &&
                    !isWeChatBankOfficialAlert
                ) {
                    return null
                }
            }
            else -> Unit
        }

        val matched = matchStructuredBill(sanitized) ?: return null
        val type = matched.first
        val amountCents = matched.second
        val counterpartyFromTemplate = matched.third

        if (amountCents <= 0L) return null

        val merchant = sanitizeMerchant(counterpartyFromTemplate) ?: extractMerchant(sanitized)
        val isTransfer = sanitized.contains("转账") || sanitized.contains("已收钱") ||
            sanitized.contains("对方已收款") || sanitized.contains("转存")
        val isExplicitWeChatWallet = sanitized.contains("微信支付") ||
            sanitized.contains("微信转账") ||
            sanitized.contains("零钱") ||
            sanitized.contains("财付通")

        val paymentMethod = when {
            isWeChatBankOfficialAlert && !isExplicitWeChatWallet -> "银行卡"
            channelGroup == NotificationChannelGroup.WECHAT ||
                sanitized.contains("微信") ||
                sanitized.contains("财付通") -> "微信支付"
            channelGroup == NotificationChannelGroup.ALIPAY || sanitized.contains("支付宝") -> "支付宝"
            channelGroup == NotificationChannelGroup.UNIONPAY ||
                channelGroup == NotificationChannelGroup.BANK_APP ||
                channelGroup == NotificationChannelGroup.BANK_SMS ||
                bankLabel != null ||
                sanitized.contains("银行") ||
                sanitized.contains("信用卡") ||
                sanitized.contains("储蓄卡") -> "银行卡"
            else -> "其他"
        }

        val scopedCategories = if (type == "income") incomeCategories else expenseCategories
        val categoryHintText = if (merchant != null) "$merchant $sanitized" else sanitized
        val category = FormatUtils.inferCategoryFromText(
            text = categoryHintText,
            knownCategories = scopedCategories,
            type = type
        )

        val title = when {
            type == "income" && isTransfer && merchant != null -> "收到转账 · $merchant"
            type == "income" && merchant != null -> "收到款项 · $merchant"
            type == "income" && bankLabel != null -> "$bankLabel · 入账"
            type == "income" && isTransfer -> "微信转账收款"
            type == "income" -> "自动记账: 收到款项"
            isTransfer && merchant != null -> "转账 · $merchant"
            merchant != null -> merchant
            isTransfer && paymentMethod == "微信支付" -> "微信转账"
            paymentMethod == "微信支付" -> "微信支付自动记账"
            paymentMethod == "支付宝" -> "支付宝自动记账"
            bankLabel != null -> "$bankLabel · 消费"
            paymentMethod == "银行卡" -> "银行卡自动记账"
            else -> "自动记账"
        }

        // 脱敏处理备注：隐藏 4 位以上连续数字（卡号/账号）
        val desensitizedContent = content.replace(Regex("""\d{4,}"""), "****")
        val sanitizedNote = "自动记账 ($paymentMethod - $category): $desensitizedContent"

        val request = CreateTransactionRequest(
            amount = amountCents,
            type = type,
            category = category,
            title = title,
            note = sanitizedNote,
            paymentMethod = paymentMethod,
            occurredAt = FormatUtils.toBeijingIsoString(LocalDateTime.now(FormatUtils.BeijingZone))
        )

        val effectiveChannelGroup = if (isWeChatBankOfficialAlert && !isExplicitWeChatWallet) {
            NotificationChannelGroup.BANK_APP
        } else {
            channelGroup
        }
        val channelHint = inferChannelHint(effectiveChannelGroup, sanitized)
        val notifyOrigin = when (effectiveChannelGroup) {
            NotificationChannelGroup.WECHAT,
            NotificationChannelGroup.WECHAT_SCREEN,
            NotificationChannelGroup.ALIPAY,
            NotificationChannelGroup.ALIPAY_SCREEN,
            NotificationChannelGroup.E_COMMERCE -> ORIGIN_WALLET
            NotificationChannelGroup.BANK_APP,
            NotificationChannelGroup.UNIONPAY,
            NotificationChannelGroup.BANK_SMS -> ORIGIN_BANK
        }

        // 质量分：用于跨平台多提醒到达时的优先级仲裁（完成页 > 钱包 > 银行 App/电商 > 短信；有商户/具体分类加分）
        val qualityScore = effectiveChannelGroup.basePriority * 10 +
            (if (merchant != null) 4 else 0) +
            (if (category != "其他" && category != "其他收入") 2 else 0)

        return ParsedAutoLedgerBill(
            request = request,
            channelGroup = effectiveChannelGroup,
            merchant = merchant,
            normalizedContent = normalizeContentForDedupe(content),
            qualityScore = qualityScore,
            channelHint = channelHint,
            notifyOrigin = notifyOrigin
        )
    }

    private fun inferChannelFromContent(content: String): NotificationChannelGroup = when {
        content.contains("微信") -> NotificationChannelGroup.WECHAT
        content.contains("支付宝") -> NotificationChannelGroup.ALIPAY
        content.contains("云闪付") || content.contains("银联") -> NotificationChannelGroup.UNIONPAY
        BANK_IDENTIFIERS.any { (kw, _) -> content.contains(kw) } ||
            content.contains("银行") || content.contains("信用卡") || content.contains("储蓄卡") ->
            NotificationChannelGroup.BANK_APP
        else -> NotificationChannelGroup.BANK_SMS
    }

    internal fun inferChannelHint(channelGroup: NotificationChannelGroup, text: String): String? {
        when (channelGroup) {
            NotificationChannelGroup.WECHAT, NotificationChannelGroup.WECHAT_SCREEN -> return "微信"
            NotificationChannelGroup.ALIPAY, NotificationChannelGroup.ALIPAY_SCREEN -> return "支付宝"
            else -> Unit
        }
        return when {
            text.contains("财付通") || text.contains("微信支付") || text.contains("微信转账") -> "微信"
            text.contains("支付宝") -> "支付宝"
            text.contains("抖音支付") -> "抖音"
            text.contains("网银在线") || text.contains("京东支付") -> "京东"
            else -> null
        }
    }

    private fun identifyBankLabel(text: String): String? {
        for ((kw, label) in BANK_IDENTIFIERS) {
            if (text.contains(kw)) return label
        }
        return null
    }

    private fun matchStructuredBill(sanitized: String): Triple<String, Long, String?>? {
        // 0. 明确“对方已收款/朋友已收款”转出确认回执（优先于收入匹配）
        OUTGOING_TRANSFER_ACCEPTED_PATTERN.matcher(sanitized).let { m ->
            if (m.find()) {
                extractFirstAmount(m)?.let { return Triple("expense", it, null) }
            }
        }

        // 1. 明确收入（带转账人）模板
        INCOME_FROM_RECEIVED_PATTERN.matcher(sanitized).let { m ->
            if (m.find()) {
                val sender = m.group(1)?.trim()
                if (!isPromoEntity(sender)) {
                    extractFirstAmount(m, startGroup = 2)?.let { return Triple("income", it, sender) }
                }
            }
        }
        INCOME_FROM_SENT_TO_ME_PATTERN.matcher(sanitized).let { m ->
            if (m.find()) {
                val sender = m.group(1)?.trim()
                if (!isPromoEntity(sender)) {
                    extractFirstAmount(m, startGroup = 2)?.let { return Triple("income", it, sender) }
                }
            }
        }

        // 1a. 银行通用的「完成<类型>交易人民币[+-]金额」（先看显式正负号，再看交易类型语义）
        BANK_COMPLETED_TX_PATTERN.matcher(sanitized).let { m ->
            if (m.find()) {
                val txType = m.group(1)?.trim().orEmpty()
                val sign = m.group(2)?.trim().orEmpty()
                val cents = parseYuanStringToCents(m.group(3))
                if (cents != null) {
                    val direction = when {
                        sign == "-" -> "expense"
                        sign == "+" -> "income"
                        BANK_COMPLETED_INCOME_HINTS.any { txType.contains(it) } -> "income"
                        BANK_COMPLETED_EXPENSE_HINTS.any { txType.contains(it, ignoreCase = true) } -> "expense"
                        else -> null
                    }
                    if (direction != null) {
                        return Triple(direction, cents, null)
                    }
                }
            }
        }

        // 1b. 明确收入直出与反序模板
        INCOME_DIRECT_PATTERN.matcher(sanitized).let { m ->
            if (m.find()) {
                extractFirstAmount(m)?.let { return Triple("income", it, null) }
            }
        }
        INCOME_REVERSE_PATTERN.matcher(sanitized).let { m ->
            if (m.find()) {
                extractFirstAmount(m)?.let { return Triple("income", it, null) }
            }
        }

        // 2. 银行带正负号动账模板（优先于无符号直出模板，确保「+500.00」不被当成支出）
        SIGNED_EXPENSE_PATTERN.matcher(sanitized).let { m ->
            if (m.find()) {
                extractFirstAmount(m)?.let { return Triple("expense", it, null) }
            }
        }
        SIGNED_INCOME_PATTERN.matcher(sanitized).let { m ->
            if (m.find()) {
                extractFirstAmount(m)?.let { return Triple("income", it, null) }
            }
        }

        // 3. 明确支出（带商户/收款人/扣款卡）模板
        EXPENSE_PAYEE_VERB_AFTER_PATTERN.matcher(sanitized).let { m ->
            if (m.find()) {
                val payee = m.group(1)?.trim()
                if (!isPromoEntity(payee)) {
                    extractFirstAmount(m, startGroup = 2)?.let { return Triple("expense", it, payee) }
                }
            }
        }
        EXPENSE_PAYEE_VERB_BEFORE_PATTERN.matcher(sanitized).let { m ->
            if (m.find()) {
                val payee = m.group(1)?.trim()
                if (!isPromoEntity(payee)) {
                    extractFirstAmount(m, startGroup = 2)?.let { return Triple("expense", it, payee) }
                }
            }
        }
        EXPENSE_FROM_ACCOUNT_PATTERN.matcher(sanitized).let { m ->
            if (m.find()) {
                extractFirstAmount(m)?.let { return Triple("expense", it, null) }
            }
        }

        // 4. 明确支出反序模板（「你有一笔 36.50 元的支出/转账」）
        EXPENSE_REVERSE_PATTERN.matcher(sanitized).let { m ->
            if (m.find()) {
                extractFirstAmount(m)?.let { return Triple("expense", it, null) }
            }
        }

        // 5. 明确支出直出模板（含微信转账、支付宝扣款、农行完成财付通交易、零钱支付等）
        EXPENSE_DIRECT_PATTERN.matcher(sanitized).let { m ->
            if (m.find()) {
                extractFirstAmount(m)?.let { return Triple("expense", it, null) }
            }
        }

        // 5. 受控成交凭证兜底（绝不对无成交关键词的普通通知按 ¥ 盲目记账）
        if (INCOME_CUE_PATTERN.matcher(sanitized).find()) {
            CUE_AMOUNT_PATTERN.matcher(sanitized).let { m ->
                if (m.find()) {
                    extractFirstAmount(m)?.let { return Triple("income", it, null) }
                }
            }
        }
        if (EXPENSE_CUE_PATTERN.matcher(sanitized).find()) {
            CUE_AMOUNT_PATTERN.matcher(sanitized).let { m ->
                if (m.find()) {
                    extractFirstAmount(m)?.let { return Triple("expense", it, null) }
                }
            }
        }

        return null
    }

    private fun isPromoEntity(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        return name.contains("红包") || name.contains("券") || name.contains("优惠") || name.contains("立减")
    }

    private fun extractFirstAmount(matcher: Matcher, startGroup: Int = 1): Long? {
        for (idx in startGroup..matcher.groupCount()) {
            val raw = matcher.group(idx)
            if (!raw.isNullOrBlank()) {
                return parseYuanStringToCents(raw)
            }
        }
        return null
    }

    internal fun extractMerchant(sanitized: String): String? {
        for (pattern in MERCHANT_PATTERNS) {
            val matcher = pattern.matcher(sanitized)
            if (matcher.find()) {
                val candidate = sanitizeMerchant(matcher.group(1))
                if (candidate != null) return candidate
            }
        }
        return null
    }

    internal fun sanitizeMerchant(raw: String?): String? {
        var cleaned = raw
            ?.trim()
            ?.trim('：', ':', '，', ',', '。', ' ', '-', '—', '【', '】')
            .orEmpty()
        // 仅剥离成对的最外层括号（如「(星巴克)」->「星巴克」），保留昵称内部的括号（如「小王(财务)」）
        while ((cleaned.startsWith("(") && cleaned.endsWith(")")) ||
            (cleaned.startsWith("（") && cleaned.endsWith("）"))
        ) {
            cleaned = cleaned.substring(1, cleaned.length - 1).trim()
        }
        if (cleaned.length < 2 || cleaned.length > 24) return null
        if (cleaned in NON_MERCHANT_LABELS) return null
        if (cleaned.contains("红包") ||
            cleaned.contains("优惠") ||
            cleaned.contains("立减") ||
            cleaned.contains("余额") ||
            cleaned.contains("人民币") ||
            cleaned.contains("微信转账") ||
            cleaned.contains("转账支出") ||
            cleaned.contains("转账成功") ||
            cleaned.contains("平台商户")
        ) {
            return null
        }
        if (cleaned.endsWith("卡") || cleaned.contains("尾号") || cleaned.contains("账户")) {
            return null
        }
        return cleaned
    }
}
