package com.example.dianpinghelper.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.dianpinghelper.model.ShopInfo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// 单例 DataStore
private val Context.dataStore by preferencesDataStore(name = "dianping_settings")

/**
 * 收藏记录 + Cookie 持久化管理
 */
class SettingsRepository(private val context: Context) {

    private val gson = Gson()

    companion object {
        private val KEY_HISTORY = stringPreferencesKey("favorite_history")
        private val KEY_COOKIES = stringPreferencesKey("webview_cookies")
    }

    // ── 收藏历史 ──────────────────────────────

    /** 获取收藏历史列表（Flow 响应式） */
    val favoriteHistoryFlow: Flow<List<ShopInfo>> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_HISTORY] ?: return@map emptyList()
        try {
            val type = object : TypeToken<List<ShopInfo>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 读取当前收藏历史（挂起函数） */
    suspend fun getFavoriteHistory(): List<ShopInfo> {
        return favoriteHistoryFlow.first()
    }

    /** 添加一条收藏记录 */
    suspend fun addFavorite(shop: ShopInfo) {
        context.dataStore.edit { prefs ->
            val json = prefs[KEY_HISTORY] ?: "[]"
            val list: MutableList<ShopInfo> = try {
                val type = object : TypeToken<List<ShopInfo>>() {}.type
                (gson.fromJson(json, type) as? List<ShopInfo>)?.toMutableList()
                    ?: mutableListOf()
            } catch (_: Exception) {
                mutableListOf()
            }
            list.add(0, shop)  // 最新在最前
            if (list.size > 200) list.removeAt(list.lastIndex) // 限制 200 条
            prefs[KEY_HISTORY] = gson.toJson(list)
        }
    }

    /** 删除一条收藏记录 */
    suspend fun removeFavorite(index: Int) {
        context.dataStore.edit { prefs ->
            val json = prefs[KEY_HISTORY] ?: return@edit
            try {
                val type = object : TypeToken<List<ShopInfo>>() {}.type
                val list = (gson.fromJson(json, type) as? MutableList<ShopInfo>) ?: return@edit
                if (index in list.indices) {
                    list.removeAt(index)
                    prefs[KEY_HISTORY] = gson.toJson(list)
                }
            } catch (_: Exception) {}
        }
    }

    // ── WebView Cookie ────────────────────────

    /** 保存 WebView Cookie */
    suspend fun saveCookies(cookies: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_COOKIES] = cookies
        }
    }

    /** 读取 WebView Cookie */
    suspend fun getCookies(): String {
        return context.dataStore.data.first()[KEY_COOKIES] ?: ""
    }
}
