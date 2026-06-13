package com.example.dianpinghelper.extractor

import com.example.dianpinghelper.model.ShopInfo

/**
 * 店铺信息提取引擎
 *
 * 使用正则规则从文本中提取店铺名称和地址。
 * 支持抖音、小红书、B站及通用文字格式。
 * 支持提取文本中的多个店铺。
 */
object ShopInfoExtractor {

    // ── 正则规则 ──────────────────────────────

    /** 「店名：xxx」/「店铺：xxx」 */
    private val SHOP_NAME_PREFIX = Regex(
        "(?:店铺?[名称叫]?|门店?[名称叫]?|探店)\\s*[：:]\\s*(.{2,40})"
    )

    /** 「📍店铺名」或「📍 店铺名」 */
    private val LOCATION_PIN = Regex("[📍📌]\\s*(.{2,30})")

    /** 抖音常见：「店铺名(地址)」 */
    private val DOUYIN_SHOP = Regex(
        "(?:📍|🏪)?\\s*([\\u4e00-\\u9fff\\w]{2,20})\\s*[（(]\\s*([^）)]{5,40})\\s*[）)]"
    )

    /** 小红书常见：「店名：xxx 📍地址」 */
    private val XIAOHONGSHU = Regex(
        "(?:店名|店铺|探店)[：:]\\s*([\\u4e00-\\u9fff\\w]{2,30})[\\s\\n]*[📍📌]\\s*([\\u4e00-\\u9fff\\w/\\\\,，。\\s\\-]{5,50})"
    )

    /** 「地址：xxx」 */
    private val ADDRESS_PREFIX = Regex(
        "(?:地址|位置|地点|坐标|位于)\\s*[：:]\\s*(.{5,60})"
    )

    /** 地址片段：省市开头 */
    private val ADDRESS_PATTERN = Regex(
        "([省市自治区].{2,30}(?:路|街|巷|号|大厦|广场|中心|商场|城|苑|园|区|栋|楼|层))"
    )

    /** 抖音/B站/小红书 URL 检测 */
    private val PLATFORM_URLS = listOf(
        "douyin.com" to "抖音",
        "iesdouyin.com" to "抖音",
        "xiaohongshu.com" to "小红书",
        "xhslink.com" to "小红书",
        "bilibili.com" to "B站",
        "b23.tv" to "B站",
    )

    /** 数字序号前缀，如 "1." "2、" "③" */
    private val NUMBER_PREFIX = Regex("""(?:^|\n)\s*(?:\d+[.、\)\)]|[①②③④⑤⑥⑦⑧⑨⑩])\s*""")

    // ── 公开接口 ──────────────────────────────

    /**
     * 从文本中提取第一个店铺信息（兼容旧接口）
     */
    fun extract(text: String): ShopInfo? {
        return extractAll(text).firstOrNull()
    }

    /**
     * 从文本中提取所有店铺信息
     */
    fun extractAll(text: String): List<ShopInfo> {
        if (text.isBlank()) return emptyList()

        val platform = detectPlatform(text)
        val shops = mutableListOf<ShopInfo>()

        // 1. 先用高精度模式匹配
        val highPrecision = extractHighPrecision(text, platform)
        shops.addAll(highPrecision)

        // 2. 如果没有匹配到，用通用模式
        if (shops.isEmpty()) {
            val general = extractGeneral(text)
            shops.addAll(general)
        }

        // 去重（同名去重）
        val seen = mutableSetOf<String>()
        return shops.filter { seen.add(it.name) }.map { shop ->
            shop.copy(city = guessCity(text, shop.address), sourceText = text)
        }
    }

    /**
     * 检测文本来自哪个平台
     */
    fun detectPlatform(text: String): String? {
        for ((domain, platform) in PLATFORM_URLS) {
            if (text.contains(domain, ignoreCase = true)) return platform
        }
        return null
    }

    // ── 高精度提取（小红书/抖音等明确格式） ─────

    private fun extractHighPrecision(text: String, platform: String?): List<ShopInfo> {
        val results = mutableListOf<ShopInfo>()

        // 小红书格式：店名：xxx 📍地址
        val xhsMatches = XIAOHONGSHU.findAll(text)
        for (m in xhsMatches) {
            results.add(ShopInfo(
                name = m.groupValues[1].trim(),
                address = m.groupValues[2].trim(),
            ))
        }

        // 抖音格式：店铺名(地址)
        val dyMatches = DOUYIN_SHOP.findAll(text)
        for (m in dyMatches) {
            val name = m.groupValues[1].trim()
            val addr = m.groupValues[2].trim()
            // 避免与小红书结果重复
            if (results.none { it.name == name }) {
                results.add(ShopInfo(name = name, address = addr))
            }
        }

        // 多个 "店名：xxx" 模式（按行拆分匹配）
        val segments = text.split("\n")
        if (segments.size >= 3) {
            var i = 0
            while (i < segments.size) {
                val line = segments[i].trim()
                val nameMatch = SHOP_NAME_PREFIX.find(line)
                if (nameMatch != null) {
                    val name = nameMatch.groupValues[1].trim()
                        .replace(Regex("[，,。\\s]+$"), "")
                    // 找地址：当前行或下一行
                    var addr = ""
                    val addrInLine = ADDRESS_PREFIX.find(line)
                    if (addrInLine != null) {
                        addr = addrInLine.groupValues[1].trim()
                    } else if (i + 1 < segments.size) {
                        val nextLine = segments[i + 1].trim()
                        val addrNext = ADDRESS_PREFIX.find(nextLine)
                        if (addrNext != null) {
                            addr = addrNext.groupValues[1].trim()
                        } else if (nextLine.length in 5..60 && ADDRESS_PATTERN.containsMatchIn(nextLine)) {
                            addr = nextLine
                        }
                    }
                    if (results.none { it.name == name }) {
                        results.add(ShopInfo(name = name, address = addr))
                    }
                }
                i++
            }
        }

        return results
    }

    // ── 通用提取（无明确格式时） ───────────────

    private fun extractGeneral(text: String): List<ShopInfo> {
        val results = mutableListOf<ShopInfo>()

        // 按序号拆分（"1.xxx 2.xxx" 或 "①xxx ②xxx"）
        val numberedSegments = NUMBER_PREFIX.split(text).map { it.trim() }.filter { it.length >= 2 }

        if (numberedSegments.size >= 2) {
            for (segment in numberedSegments) {
                val shop = extractSingleShop(segment)
                if (shop != null) results.add(shop)
            }
        }

        // 如果按序号拆分没结果，尝试按行拆分
        if (results.isEmpty()) {
            val lines = text.split("\n").map { it.trim() }.filter { it.length >= 2 }
            for (line in lines) {
                // 跳过明显不是店名的行
                if (line.length > 30) continue
                if (line.contains("推荐") || line.contains("分享") || line.contains("打卡")) continue
                if (line.startsWith("http")) continue

                val shop = extractSingleShop(line)
                if (shop != null && results.none { it.name == shop.name }) {
                    results.add(shop)
                }
            }
        }

        // 最后兜底：直接在全文找第一个
        if (results.isEmpty()) {
            val single = extractSingleShop(text)
            if (single != null) results.add(single)
        }

        return results
    }

    /**
     * 从一段文字中提取单个店铺
     */
    private fun extractSingleShop(text: String): ShopInfo? {
        if (text.isBlank()) return null

        // 尝试 "店名：xxx" 模式
        val nameMatch = SHOP_NAME_PREFIX.find(text)
        if (nameMatch != null) {
            val name = nameMatch.groupValues[1].trim()
                .replace(Regex("[，,。\\s]+$"), "")
            var address = ""
            val addrMatch = ADDRESS_PREFIX.find(text)
            if (addrMatch != null) {
                address = addrMatch.groupValues[1].trim()
            }
            return ShopInfo(name = name, address = address)
        }

        // 尝试 📍 标记
        val pinMatch = LOCATION_PIN.find(text)
        if (pinMatch != null) {
            val name = pinMatch.groupValues[1].trim()
            var address = ""
            val addrMatch = ADDRESS_PREFIX.find(text)
            if (addrMatch != null) {
                address = addrMatch.groupValues[1].trim()
            }
            return ShopInfo(name = name, address = address)
        }

        // 尝试【】括号
        val bracketMatch = Regex("[【\\[](.*?)[】\\]]").find(text)
        if (bracketMatch != null) {
            val name = bracketMatch.groupValues[1].trim()
            val skipKeywords = listOf("推荐", "打卡", "分享", "收藏", "攻略")
            if (name.length >= 2 && skipKeywords.none { name.contains(it) }) {
                var address = ""
                val addrMatch = ADDRESS_PREFIX.find(text)
                if (addrMatch != null) {
                    address = addrMatch.groupValues[1].trim()
                }
                return ShopInfo(name = name, address = address)
            }
        }

        return null
    }

    // ── 辅助 ──────────────────────────────────

    private fun guessCity(text: String, address: String?): String {
        val cities = listOf(
            "北京", "上海", "广州", "深圳", "成都", "杭州", "武汉", "西安",
            "南京", "重庆", "长沙", "苏州", "天津", "郑州", "东莞", "青岛",
            "沈阳", "宁波", "昆明", "大连", "厦门", "合肥", "佛山", "福州",
            "哈尔滨", "济南", "温州", "南宁", "长春", "泉州", "石家庄",
        )
        val searchText = text + (address ?: "")
        for (city in cities) {
            if (searchText.contains(city)) return city
        }
        return ""
    }
}
