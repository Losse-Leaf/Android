package com.looseleaf.app.utils

import java.util.Locale

enum class CategorizationConfidence {
    HIGH,
    MEDIUM,
    LOW
}

data class CategorizationInput(
    val type: String = "expense",
    val merchant: String? = null,
    val rawText: String = "",
    val amountCents: Long = 0L,
    val isTransfer: Boolean = false,
    val paymentMethod: String? = null,
    val knownCategories: List<String> = emptyList(),
    val categoryKeywords: Map<String, List<String>> = emptyMap(),
    val learnedMerchantRules: Map<String, String> = emptyMap()
)

data class CategorizationResult(
    val category: String,
    val confidence: CategorizationConfidence,
    val matchedReason: String
)

/**
 * 零 AI 确定性多级账目分类决策引擎 (TransactionCategorizer)
 *
 * 五阶段决策管线：
 * 0. 剥离支付/通知模板固定噪音词（彻底根除「商户→购物」「微信/腾讯→娱乐」等污染）；
 * 1. 商户历史记忆优先（精确商户名 / 去门店括号核心名）；
 * 2. 用户自定义分类关键词 & 动态分类名直接命中；
 * 3. 四档加权内置知识库（复合消歧词 > 高频品牌/场景 > 商户行业后缀正则 > 泛平台兜底），
 *    并用桶级优雅降级链适配用户自定义/精简分类字典；
 * 4. 转账上下文与安全兜底。
 */
object TransactionCategorizer {

    private val WHITESPACE_REGEX = Regex("""\s+""")
    private val BRANCH_SUFFIX_REGEX = Regex(
        """(?:\s*[（(][^（）()]{1,16}(?:店|分店|广场|校区|中心|站|支行|网点)[)）])\s*$"""
    )

    private val GENERIC_MERCHANT_PLACEHOLDERS = setOf(
        "自动记账", "微信支付自动记账", "支付宝自动记账", "银行卡自动记账",
        "微信转账", "支付宝转账", "微信转账收款", "收到款项",
        "消费", "入账", "收款", "付款", "转账"
    )

    private val GENERIC_MERCHANT_PREFIXES = listOf(
        "转账 · ", "收到转账 · ", "收到款项 · ",
        "财付通-", "财付通—", "支付宝-", "支付宝—", "微信支付-", "微信支付—"
    )

    // 剥离通知模板固定词，防止它们污染正文分类判断
    private val BOILERPLATE_WORDS = listOf(
        "微信支付凭证", "微信收款商业版", "微信收款助手", "微信支付", "微信转账",
        "支付宝到账", "支付宝收款", "支付宝", "支付助手", "花呗", "余额宝",
        "财付通", "云闪付", "网联", "银联", "自动记账",
        "收款商户", "交易商户", "消费商户", "平台商户", "收款方", "收款人", "付款方", "交易对方",
        "商户", "商家", "储蓄卡", "信用卡", "借记卡", "银行卡", "零钱通", "零钱",
        "你已向", "您已向", "交易提醒", "账单提醒", "动账通知", "动账", "交易成功",
        "支付成功", "付款成功", "成功付款", "扣款成功", "扣费成功", "自动扣款", "免密支付",
        "消费成功", "快捷支付", "网上支付", "扫码支付", "扫码付款", "刷卡消费", "微信", "腾讯"
    )

    private val DEFAULT_EXPENSE_FALLBACK = listOf("其他", "餐饮", "购物")
    private val DEFAULT_INCOME_FALLBACK = listOf("其他", "其他收入", "工资", "工资收入")

    private val EXPENSE_FALLBACK_CHAINS: Map<String, List<String>> = mapOf(
        "餐饮" to listOf("餐饮", "美食", "日常饮食"),
        "交通" to listOf("交通", "出行"),
        "购物" to listOf("购物", "日用百货"),
        "居家" to listOf("居家", "住房"),
        "订阅" to listOf("订阅", "订阅会员", "娱乐", "学习"),
        "娱乐" to listOf("娱乐", "休闲"),
        "医疗" to listOf("医疗", "健康"),
        "学习" to listOf("学习", "办公", "教育"),
        "人情社交" to listOf("人情社交", "其他")
    )

    private val INCOME_FALLBACK_CHAINS: Map<String, List<String>> = mapOf(
        "工资" to listOf("工资", "工资收入", "薪资", "月薪", "奖金"),
        "副业" to listOf("副业", "兼职副业", "稿酬设计", "工资"),
        "理财" to listOf("理财", "投资理财", "投资收益"),
        "退款" to listOf("退款", "退款报销", "二手回血"),
        "亲友转账" to listOf("亲友转账", "其他", "其他收入")
    )

    private data class BucketRule(
        val bucket: String,
        val keywords: List<String>,
        val score: Int
    )

    // Tier A：复合消歧词（权重 120，确保跨平台复合词先于泛平台词命中）
    private val EXPENSE_TIER_A = listOf(
        BucketRule("医疗", listOf("美团买药", "饿了么买药", "京东大药房", "叮当快药", "阿里健康", "1药网"), 120),
        BucketRule("交通", listOf("美团打车", "高德打车", "滴滴出行", "滴滴快车", "曹操出行", "t3出行", "花小猪", "享道出行", "如祺出行", "哈啰单车", "青桔单车", "美团单车", "铁路12306", "12306"), 120),
        BucketRule("娱乐", listOf("美团酒店", "美团民宿", "携程旅行", "携程", "飞猪", "去哪儿", "同程旅行", "途牛", "马蜂窝", "airbnb", "爱彼迎", "亚朵", "全季", "汉庭", "如家", "桔子酒店", "万豪", "希尔顿", "洲际", "健身房", "乐刻", "超级猩猩", "古德菲力", "威尔士", "一兆韦德", "keep", "迪卡侬", "羽毛球馆", "网球馆", "乒乓球馆", "游泳馆", "瑜伽馆", "普拉提", "攀岩", "宠物医院", "动物医院", "波奇宠物", "皇家宠物", "猫粮", "狗粮", "猫砂"), 120),
        BucketRule("餐饮", listOf("美团优选", "美团买菜", "小象超市", "叮咚买菜", "朴朴超市", "盒马鲜生", "每日优鲜", "多多买菜", "永辉生活", "大润发优鲜"), 120),
        BucketRule("居家", listOf("国家电网", "南方电网", "网上国网", "电费", "水费", "燃气费", "煤气费", "天然气费", "暖气费", "供暖费", "水电费", "自来水", "中国移动", "中国联通", "中国电信", "中国广电", "话费充值", "手机充值", "话费", "宽带费", "宽带", "流量包", "通讯费"), 120),
        BucketRule(
            "订阅",
            listOf(
                "icloud", "app store", "apple.com/bill", "google play", "chatgpt", "openai", "claude",
                "github", "cursor", "notion", "spotify", "netflix", "youtube", "steamdeck",
                "百度网盘", "夸克网盘", "阿里云盘", "wps会员", "wps超级会员",
                "腾讯视频", "爱奇艺", "优酷", "芒果tv", "哔哩哔哩大会员", "b站大会员",
                "网易云音乐", "qq音乐", "酷狗音乐", "喜马拉雅", "微信读书", "知乎盐选", "得到会员",
                "京东plus", "88vip", "美团神会员", "饿了么超级会员", "淘宝省钱卡", "连续包月", "自动续费"
            ),
            120
        ),
        BucketRule(
            "购物",
            listOf(
                "apple store", "苹果专卖", "苹果官方", "华为商城", "小米之家", "小米有品", "大疆", "dji", "索尼", "佳能", "尼康", "富士", "罗技", "cherry", "机械键盘", "显卡", "固态硬盘", "nas",
                "顺丰速运", "顺丰", "菜鸟裹裹", "菜鸟驿站", "中通快递", "圆通", "申通", "韵达", "极兔", "京东物流", "丰巢", "快递费", "寄件",
                "丝芙兰", "sephora", "优衣库", "uniqlo", "zara", "hm", "无印良品", "muji", "lululemon", "耐克", "nike", "阿迪达斯", "adidas", "李宁", "安踏", "波司登", "珀莱雅", "兰蔻", "雅诗兰黛", "理发", "美发", "洗剪吹", "剪发"
            ),
            120
        )
    )

    // Tier B1：显式餐次与餐饮高频词（96 分）
    private val EXPENSE_TIER_B_HIGH_MEAL = listOf(
        "早餐", "早点", "早茶", "午餐", "午饭", "中餐", "晚餐", "晚饭", "夜宵", "宵夜", "正餐", "食堂", "工作餐"
    )

    // Tier B2：高频品牌与场景词（merchant 命中 +95，strippedText 命中 +55）
    private val EXPENSE_TIER_B = listOf(
        BucketRule(
            "餐饮",
            listOf(
                "星巴克", "starbucks", "瑞幸", "luckin", "库迪", "cotti", "manner", "m stand", "seesaw", "tims",
                "蓝瓶", "皮爷", "peet", "喜茶", "奈雪", "霸王茶姬", "茶百道", "古茗", "蜜雪冰城", "沪上阿姨",
                "一点点", "coco", "都可", "书亦烧仙草", "益禾堂", "茶颜悦色", "茉莉奶白", "爷爷不泡茶", "乐乐茶",
                "手冲咖啡", "拿铁", "美式咖啡", "生椰拿铁", "咖啡", "奶茶", "果茶", "柠檬茶", "饮品",
                "麦当劳", "肯德基", "kfc", "汉堡王", "必胜客", "萨莉亚", "达美乐", "塔斯汀", "华莱士",
                "海底捞", "巴奴", "凑凑", "呷哺", "太二", "西贝", "绿茶餐厅", "外婆家", "费大厨", "小菜园",
                "老乡鸡", "大米先生", "乡村基", "真功夫", "吉野家", "食其家", "和府捞面", "陈香贵", "马记永",
                "张亮麻辣烫", "杨国福", "袁记云饺", "沙县小吃", "兰州拉面", "黄焖鸡", "螺蛳粉", "酸菜鱼",
                "饿了么", "美团外卖", "外卖", "快餐", "火锅", "烧烤", "烤肉", "日料", "寿司", "拉面", "米线",
                "麻辣烫", "冒菜", "小吃", "聚餐", "干饭", "吃饭", "请客吃饭",
                "买菜", "菜市场", "农贸市场", "生鲜", "盒马", "叮咚", "朴朴", "钱大妈", "永辉", "菜场",
                "百果园", "鲜丰水果", "切果now", "果切", "水果", "三只松鼠", "良品铺子", "来伊份", "盐津铺子",
                "好利来", "鲍师傅", "味多美", "85度c", "巴黎贝甜", "面包新语", "泸溪河", "桃李", "烘焙",
                "面包", "蛋糕", "甜品", "甜点", "冰淇淋", "哈根达斯", "dq", "零食", "薯片", "坚果", "巧克力"
            ),
            95
        ),
        BucketRule(
            "交通",
            listOf(
                "地铁", "公交", "打车", "网约车", "出租车", "顺风车", "滴滴", "高德", "曹操", "神州专车",
                "火车", "高铁", "动车", "机票", "航空", "南方航空", "东方航空", "中国国航", "春秋航空", "吉祥航空",
                "航旅", "加油", "中石油", "中石化", "中海油", "壳牌", "道达尔", "充电桩", "特来电", "星星充电",
                "停车费", "停车场", "过路费", "高速费", "etc", "哈啰", "青桔", "骑行", "单车", "共享单车", "通勤"
            ),
            95
        ),
        BucketRule(
            "购物",
            listOf(
                "罗森", "lawson", "全家", "familymart", "7-eleven", "711", "便利蜂", "美宜佳", "喜士多", "十足",
                "沃尔玛", "山姆", "costco", "开市客", "大润发", "物美", "家乐福", "华润万家", "名创优品", "miniso",
                "屈臣氏", "超市", "便利店", "百货", "纸巾", "抽纸", "洗衣液", "洗洁精", "牙膏", "洗发水", "日用品", "快递",
                "衣服", "裤子", "外套", "卫衣", "衬衫", "连衣裙", "球鞋", "运动鞋", "皮鞋", "包包", "背包", "帽子", "口红", "面霜", "防晒", "精华", "粉底", "护肤", "化妆品", "美甲", "烫发", "染发",
                "手机", "iphone", "ipad", "macbook", "airpods", "相机", "镜头", "单反", "微单", "电脑", "笔记本", "显示器", "耳机", "键盘", "鼠标", "路由器", "充电宝", "数据线", "数码", "电子产品", "鲜花", "花店", "礼物"
            ),
            95
        ),
        BucketRule(
            "居家",
            listOf("房租", "租金", "月租", "房贷", "按揭", "物业费", "物业", "取暖", "宜家", "ikea", "家装", "家具", "家电", "装修", "维修", "保洁", "家政"),
            95
        ),
        BucketRule(
            "订阅",
            listOf("会员费", "会员续费", "月卡", "季卡", "年卡", "云盘", "网盘", "软件订阅", "订阅"),
            95
        ),
        BucketRule(
            "医疗",
            listOf("医院", "门诊", "挂号", "医保", "诊所", "体检", "拔牙", "洗牙", "补牙", "牙科", "口腔", "眼科", "配镜", "药房", "药店", "大药房", "老百姓大药房", "益丰", "海王星辰", "买药", "药品", "挂水"),
            95
        ),
        BucketRule(
            "学习",
            listOf("学费", "培训", "网课", "课程", "考研", "考公", "雅思", "托福", "驾校", "报名费", "考试费", "新华书店", "西西弗", "当当", "购书", "买书", "图书", "书籍", "教材", "文具", "晨光", "得力", "打印", "复印"),
            95
        ),
        BucketRule(
            "娱乐",
            listOf(
                "电影", "万达影城", "cgv", "淘票票", "猫眼", "剧本杀", "密室", "桌游", "ktv", "酒吧", "清吧", "livehouse", "演唱会", "音乐节", "话剧", "展览", "美术馆", "博物馆", "游戏", "steam", "switch", "psn", "xbox", "原神", "王者", "泡泡玛特", "盲盒", "电玩",
                "酒店", "民宿", "客栈", "旅馆", "景点", "门票", "景区", "缆车", "游乐园", "迪士尼", "环球影城", "旅游", "度假", "露营", "签证",
                "健身", "私教", "游泳", "瑜伽", "跑步", "羽毛球", "篮球", "足球", "网球", "乒乓球", "滑雪", "爬山", "按摩", "推拿", "足疗", "理疗",
                "宠物", "猫咖", "狗咖", "宠物美容", "宠物洗澡", "驱虫", "疫苗"
            ),
            95
        )
    )

    // Tier C：商户行业后缀正则（仅针对清洗后的 merchant 字段，避免正文噪音误触发）
    private val EXPENSE_MERCHANT_SUFFIX_RULES: List<Pair<String, Regex>> = listOf(
        "餐饮" to Regex("""(?:咖啡|茶饮|奶茶|茶室|茶馆|茶社|饮品店|面馆|小馆|饭店|餐馆|餐厅|烧烤|火锅|烤肉|小厨|食府|小吃|快餐|粥铺|粉面|米线|拉面|饺子|汤包|砂锅|卤味|炸鸡|汉堡|料理|居酒屋|食堂|水果|果业|果园|烘焙|面包|蛋糕|甜品|零食|茶点|糕点|生鲜|菜场|菜市|农贸|肉业|水产|海鲜)$"""),
        "购物" to Regex("""(?:超市|便利店|百货|副食|日杂|干洗|洗衣|快递|驿站|花艺|花店|鲜花|礼品)$"""),
        "购物" to Regex("""(?:理发|美发|造型|美容|美甲|美睫|服饰|服装|男装|女装|童装|鞋业|皮具)(?:店|室|馆|工作室)?$"""),
        "医疗" to Regex("""(?:药房|药店|大药房|医院|诊所|卫生室|口腔|牙科|眼科|体检中心)$"""),
        "交通" to Regex("""(?:加油站|停车场|停车|充电站|汽修|洗车|汽车服务|出行)$"""),
        "娱乐" to Regex("""(?:酒店|宾馆|民宿|客栈|旅社|度假村|景区|文旅|健身|健身房|球馆|游泳馆|瑜伽|体育|运动中心|足浴|推拿|宠物|猫咖|动物医院|宠物店|影城|影院|电影院|电玩|桌游|密室|ktv|酒吧|台球|网咖|电竞)$""", RegexOption.IGNORE_CASE),
        "学习" to Regex("""(?:书店|图书|教育|培训|驾校|文具|图文|打印)$"""),
        "居家" to Regex("""(?:水务|燃气|热力|供暖|电网|供电|物业|物业管理|房产|置业|家政|保洁|通信|通讯|营业厅|宽带)$""")
    )

    // Tier D：泛平台弱兜底（权重 30）
    private val EXPENSE_TIER_D = listOf(
        BucketRule("餐饮", listOf("美团"), 30),
        BucketRule("购物", listOf("淘宝", "天猫", "京东", "拼多多", "抖音商城", "得物", "小红书商城", "唯品会", "闲鱼"), 30)
    )

    private val INCOME_RULES = listOf(
        BucketRule("工资", listOf("代发工资", "工资", "薪资", "发薪", "薪水", "底薪", "月薪", "年终奖", "奖金", "绩效", "提成", "津贴", "补贴", "房补", "餐补", "加班费"), 100),
        BucketRule("副业", listOf("兼职", "副业", "外包", "劳务费", "咨询费", "讲课费", "稿费", "稿酬", "设计费", "约稿", "版权", "版税", "创作激励", "打赏", "赞赏"), 95),
        BucketRule("理财", listOf("理财", "基金", "股票", "股息", "分红", "利息", "结息", "收益", "余额宝", "零钱通收益", "国债"), 95),
        BucketRule("退款", listOf("退款", "退货", "退费", "报销", "差旅报销", "返现", "退税", "个税退税", "赔付", "理赔", "退回", "退汇", "二手", "闲鱼", "转转", "爱回收", "回血", "出售", "出闲置"), 95),
        BucketRule("其他", listOf("红包", "礼金", "压岁钱", "份子钱", "微信转账收款", "收到转账", "好友转账", "亲友转账"), 50)
    )

    /**
     * 规范化商户名/交易标题为稳定的归类记忆键与展示名。
     * 返回 null 表示通用占位词、纯数字或过短，不应作为商户记忆。
     */
    fun normalizeMerchantKey(raw: String?): Pair<String, String>? {
        var cleaned = raw?.trim().orEmpty()
        if (cleaned.isEmpty()) return null
        if (cleaned.startsWith("自动记账:") || cleaned.startsWith("自动记账：") ||
            cleaned.endsWith(" · 消费") || cleaned.endsWith(" · 入账")
        ) {
            return null
        }

        while (true) {
            val matchedPrefix = GENERIC_MERCHANT_PREFIXES.firstOrNull { cleaned.startsWith(it) } ?: break
            cleaned = cleaned.removePrefix(matchedPrefix).trim()
        }

        cleaned = BRANCH_SUFFIX_REGEX.replace(cleaned, "")
        cleaned = cleaned.replace(WHITESPACE_REGEX, " ").trim()
        if (cleaned.isEmpty() || cleaned in GENERIC_MERCHANT_PLACEHOLDERS) return null
        if (cleaned.length < 2) return null

        val hasMeaningfulChar = cleaned.any { !it.isDigit() && !it.isWhitespace() && Character.getType(it) != Character.OTHER_PUNCTUATION.toInt() }
        if (!hasMeaningfulChar) return null

        val capped = if (cleaned.length > 120) cleaned.substring(0, 120) else cleaned
        return capped.lowercase(Locale.ROOT) to capped
    }

    /** 抽取仅去除前缀与门店括号的核心商户键，供二级模糊匹配 */
    fun extractCoreMerchantKey(raw: String?): String? = normalizeMerchantKey(raw)?.first

    /** 剥离支付通知/银行模板噪音词，保留真正表达消费内容的词语 */
    fun stripNotificationBoilerplate(raw: String): String {
        var cleaned = raw.trim()
        for (word in BOILERPLATE_WORDS) {
            cleaned = cleaned.replace(word, " ", ignoreCase = true)
        }
        return cleaned.replace(WHITESPACE_REGEX, " ").trim()
    }

    /** 将逗号/换行/顿号分隔的关键词字符串解析为清洗后的关键词列表 */
    fun parseKeywords(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(',', '，', '、', ';', '；', '\n', '\r', '\t')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.ROOT) }
    }

    fun categorize(input: CategorizationInput): CategorizationResult {
        val normalizedType = if (input.type == "income") "income" else "expense"
        val validKnown = input.knownCategories.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val merchantTrimmed = input.merchant?.trim()?.takeIf { it.isNotEmpty() }
        val strippedText = stripNotificationBoilerplate(input.rawText)
        val fallbackDefault = if (normalizedType == "income") "其他收入" else "其他"

        fun resolveBucketToDictionary(bucket: String): String? {
            if (validKnown.isEmpty()) return bucket
            if (bucket in validKnown) return bucket
            val chain = if (normalizedType == "income") {
                INCOME_FALLBACK_CHAINS[bucket]
            } else {
                EXPENSE_FALLBACK_CHAINS[bucket]
            }
            chain?.firstOrNull { it in validKnown }?.let { return it }
            return null
        }

        fun ensureDictionaryFallback(): String {
            if (validKnown.isEmpty()) return fallbackDefault
            val defaults = if (normalizedType == "income") DEFAULT_INCOME_FALLBACK else DEFAULT_EXPENSE_FALLBACK
            defaults.firstOrNull { it in validKnown }?.let { return it }
            return validKnown.first()
        }

        // ── Stage 1: 用户历史商户记忆（最高优先级） ──
        if (input.learnedMerchantRules.isNotEmpty()) {
            val candidates = buildList {
                merchantTrimmed?.let { add(it) }
                if (strippedText.isNotEmpty()) add(strippedText)
            }
            for (cand in candidates) {
                val norm = normalizeMerchantKey(cand)
                val exactKey = norm?.first ?: cand.lowercase(Locale.ROOT)
                val hit = input.learnedMerchantRules[exactKey]
                    ?: input.learnedMerchantRules.entries
                        .sortedByDescending { it.key.length }
                        .firstOrNull { (k, _) -> k.length >= 2 && exactKey.contains(k) }
                        ?.value
                if (hit != null && (validKnown.isEmpty() || hit in validKnown)) {
                    return CategorizationResult(
                        category = hit,
                        confidence = CategorizationConfidence.HIGH,
                        matchedReason = "商户记忆"
                    )
                }
            }
        }

        // ── Stage 2: 用户自定义分类关键词 & 动态分类名直接命中 ──
        if (input.categoryKeywords.isNotEmpty()) {
            var bestCat: String? = null
            var bestScore = 0
            var bestKw = ""
            for ((catName, keywords) in input.categoryKeywords) {
                if (validKnown.isNotEmpty() && catName !in validKnown) continue
                for (kw in keywords) {
                    val kLower = kw.trim().lowercase(Locale.ROOT)
                    if (kLower.isEmpty()) continue
                    val hitMerchant = merchantTrimmed?.lowercase(Locale.ROOT)?.contains(kLower) == true
                    val hitText = strippedText.lowercase(Locale.ROOT).contains(kLower)
                    if (!hitMerchant && !hitText) continue
                    val score = (if (hitMerchant) 100 else 60) + minOf(kLower.length, 20)
                    if (score > bestScore) {
                        bestScore = score
                        bestCat = catName
                        bestKw = kw.trim()
                    }
                }
            }
            if (bestCat != null) {
                return CategorizationResult(
                    category = bestCat,
                    confidence = CategorizationConfidence.HIGH,
                    matchedReason = "自定义关键词·$bestKw"
                )
            }
        }

        if (validKnown.isNotEmpty()) {
            val nonFallbackKnown = validKnown
                .filterNot { it == "其他" || it == "其他收入" }
                .sortedByDescending { it.length }
            val merchantHit = merchantTrimmed?.let { m ->
                nonFallbackKnown.firstOrNull { m.contains(it, ignoreCase = true) }
            }
            if (merchantHit != null) {
                return CategorizationResult(
                    category = merchantHit,
                    confidence = CategorizationConfidence.HIGH,
                    matchedReason = "分类名命中"
                )
            }
            val textHit = nonFallbackKnown.firstOrNull { strippedText.contains(it, ignoreCase = true) }
            if (textHit != null) {
                return CategorizationResult(
                    category = textHit,
                    confidence = CategorizationConfidence.MEDIUM,
                    matchedReason = "分类名命中"
                )
            }
        }

        // ── Stage 3: 四档加权内置知识库 ──
        val merchantLower = merchantTrimmed?.lowercase(Locale.ROOT).orEmpty()
        val textLower = strippedText.lowercase(Locale.ROOT)
        val scoreMap = LinkedHashMap<String, Int>()
        val reasonMap = LinkedHashMap<String, String>()

        fun addScore(bucket: String, score: Int, reason: String) {
            val resolved = resolveBucketToDictionary(bucket) ?: return
            val prev = scoreMap[resolved] ?: 0
            val next = prev + score
            scoreMap[resolved] = next
            if (prev == 0 || score >= 90) {
                reasonMap[resolved] = reason
            }
        }

        if (normalizedType == "income") {
            for (rule in INCOME_RULES) {
                val hitKw = rule.keywords.firstOrNull { kw ->
                    (merchantLower.isNotEmpty() && merchantLower.contains(kw)) || textLower.contains(kw)
                }
                if (hitKw != null) {
                    val bonus = if (merchantLower.isNotEmpty() && merchantLower.contains(hitKw)) 15 else 0
                    addScore(rule.bucket, rule.score + bonus, "收入规则·$hitKw")
                }
            }
        } else {
            // Tier A: 复合消歧词
            for (rule in EXPENSE_TIER_A) {
                val hitKw = rule.keywords.firstOrNull { kw ->
                    (merchantLower.isNotEmpty() && merchantLower.contains(kw)) || textLower.contains(kw)
                }
                if (hitKw != null) {
                    addScore(rule.bucket, rule.score, "精准品牌·$hitKw")
                }
            }

            // Tier B1: 显式餐次优先词（避免「午餐手冲咖啡」被拆成咖啡饮品）
            val mealKw = EXPENSE_TIER_B_HIGH_MEAL.firstOrNull { kw ->
                (merchantLower.isNotEmpty() && merchantLower.contains(kw)) || textLower.contains(kw)
            }
            if (mealKw != null) {
                addScore("餐饮", 96, "餐次词·$mealKw")
            }

            // Tier B2: 高频品牌与场景词
            for (rule in EXPENSE_TIER_B) {
                val merchantKw = if (merchantLower.isNotEmpty()) {
                    rule.keywords.firstOrNull { merchantLower.contains(it) }
                } else null
                if (merchantKw != null) {
                    addScore(rule.bucket, rule.score, "品牌商户·$merchantKw")
                    continue
                }
                val textKw = rule.keywords.firstOrNull { textLower.contains(it) }
                if (textKw != null) {
                    addScore(rule.bucket, 55, "关键词·$textKw")
                }
            }

            // Tier C: 商户行业后缀正则（优先用于独立商户名；无商户名时仅在无 Tier A/B 命中时对短标题兜底，避免复合词累加串台）
            val suffixTarget = merchantTrimmed ?: strippedText.takeIf { scoreMap.isEmpty() && it.length in 2..20 }
            if (suffixTarget != null) {
                val cleanTarget = BRANCH_SUFFIX_REGEX.replace(suffixTarget, "").trim()
                for ((bucket, regex) in EXPENSE_MERCHANT_SUFFIX_RULES) {
                    if (regex.containsMatchIn(cleanTarget)) {
                        addScore(bucket, 75, "行业后缀")
                    }
                }
            }

            // Tier D: 泛平台弱兜底（仅在尚无高分候选时有效，避免美团外卖/买药以外才落到餐饮）
            for (rule in EXPENSE_TIER_D) {
                val hitKw = rule.keywords.firstOrNull { kw ->
                    (merchantLower.isNotEmpty() && merchantLower.contains(kw)) || textLower.contains(kw)
                }
                if (hitKw != null) {
                    addScore(rule.bucket, rule.score, "平台兜底·$hitKw")
                }
            }
        }

        val bestEntry = scoreMap.entries.maxByOrNull { it.value }
        if (bestEntry != null && bestEntry.value > 0) {
            val confidence = when {
                bestEntry.value >= 90 -> CategorizationConfidence.HIGH
                bestEntry.value >= 50 -> CategorizationConfidence.MEDIUM
                else -> CategorizationConfidence.LOW
            }
            return CategorizationResult(
                category = bestEntry.key,
                confidence = confidence,
                matchedReason = reasonMap[bestEntry.key].orEmpty()
            )
        }

        // ── Stage 4: 转账上下文 & 安全兜底 ──
        if (input.isTransfer) {
            val transferBucket = if (normalizedType == "income") "亲友转账" else "人情社交"
            resolveBucketToDictionary(transferBucket)?.let { resolved ->
                return CategorizationResult(
                    category = resolved,
                    confidence = CategorizationConfidence.MEDIUM,
                    matchedReason = "转账往来"
                )
            }
        }

        return CategorizationResult(
            category = ensureDictionaryFallback(),
            confidence = CategorizationConfidence.LOW,
            matchedReason = "默认兜底"
        )
    }
}
