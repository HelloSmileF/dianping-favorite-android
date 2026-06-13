package com.example.dianpinghelper

import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.content.getSystemService
import com.example.dianpinghelper.data.SettingsRepository
import com.example.dianpinghelper.model.ShopInfo
import com.example.dianpinghelper.ui.screen.*
import com.example.dianpinghelper.ui.theme.DianpingHelperTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val settingsRepo: SettingsRepository get() = DianpingApp.instance.settingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = parseSharedText(intent)

        setContent {
            DianpingHelperTheme {
                MainContent(
                    initialSharedText = sharedText,
                    settingsRepo = settingsRepo,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val sharedText = parseSharedText(intent)
        if (sharedText != null) {
            intent.removeExtra(Intent.EXTRA_TEXT)
            recreate()
        }
    }

    private fun parseSharedText(intent: Intent?): String? {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            return intent.getStringExtra(Intent.EXTRA_TEXT)
        }
        return null
    }
}

/** 页面路由 */
private enum class Screen {
    Home, ManualInput, Extract, WebView,
}

@Composable
private fun MainContent(
    initialSharedText: String?,
    settingsRepo: SettingsRepository,
) {
    var currentScreen by remember { mutableStateOf(Screen.Home) }
    var currentText by remember { mutableStateOf(initialSharedText ?: "") }

    // 多店铺队列
    var shopQueue by remember { mutableStateOf(listOf<ShopInfo>()) }
    var queueIndex by remember { mutableIntStateOf(0) }
    var currentMode by remember { mutableStateOf(FavoriteMode.WEBVIEW) }

    val history by settingsRepo.favoriteHistoryFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    // 当前正在处理的店铺
    val currentShop = if (queueIndex < shopQueue.size) shopQueue[queueIndex] else null

    LaunchedEffect(initialSharedText) {
        if (!initialSharedText.isNullOrBlank()) {
            currentText = initialSharedText
            currentScreen = Screen.Extract
        }
    }

    when (currentScreen) {
        Screen.Home -> {
            HomeScreen(
                history = history,
                onPasteClipboard = {
                    val clipboard = context.getSystemService<ClipboardManager>()
                    val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                    if (clipText.isNotBlank()) {
                        currentText = clipText
                        currentScreen = Screen.Extract
                    }
                },
                onManualInput = {
                    currentText = ""
                    currentScreen = Screen.ManualInput
                },
                onItemClick = { shop ->
                    shopQueue = listOf(shop)
                    queueIndex = 0
                    currentMode = FavoriteMode.WEBVIEW
                    currentScreen = Screen.WebView
                },
                onItemDelete = { index ->
                    scope.launch { settingsRepo.removeFavorite(index) }
                },
                onReceiveShare = { text ->
                    currentText = text
                    currentScreen = Screen.Extract
                },
            )
        }

        Screen.ManualInput -> {
            ManualInputScreen(
                initialText = currentText,
                onConfirm = { text ->
                    currentText = text
                    currentScreen = Screen.Extract
                },
                onBack = { currentScreen = Screen.Home },
            )
        }

        Screen.Extract -> {
            ExtractScreen(
                initialText = currentText,
                onConfirm = { shops, mode ->
                    shopQueue = shops
                    queueIndex = 0
                    currentMode = mode
                    currentScreen = Screen.WebView
                },
                onBack = { currentScreen = Screen.Home },
            )
        }

        Screen.WebView -> {
            val shop = currentShop
            if (shop != null) {
                WebScreen(
                    shop = shop,
                    mode = currentMode,
                    onBack = {
                        // 返回提取页
                        currentScreen = Screen.Extract
                    },
                    onSuccess = {
                        scope.launch { settingsRepo.addFavorite(shop) }
                        // 处理下一个店铺
                        val nextIndex = queueIndex + 1
                        if (nextIndex < shopQueue.size) {
                            queueIndex = nextIndex
                            // 保持 WebView 页面，currentShop 自动变为下一个
                        } else {
                            // 全部完成，回首页
                            shopQueue = emptyList()
                            queueIndex = 0
                            currentScreen = Screen.Home
                        }
                    },
                    // 多店铺进度
                    totalCount = shopQueue.size,
                    currentIndex = queueIndex,
                )
            } else {
                LaunchedEffect(Unit) {
                    shopQueue = emptyList()
                    currentScreen = Screen.Home
                }
            }
        }
    }
}
