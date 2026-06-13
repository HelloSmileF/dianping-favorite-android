package com.example.dianpinghelper.model

/**
 * 店铺信息数据模型
 */
data class ShopInfo(
    val name: String = "",
    val address: String = "",
    val city: String = "",
    val sourceText: String = "",   // 原始文本
    val timestamp: Long = System.currentTimeMillis(),
) {
    val isValid: Boolean get() = name.isNotBlank()
}
