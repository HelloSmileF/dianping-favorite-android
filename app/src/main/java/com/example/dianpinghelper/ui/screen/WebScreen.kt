package com.example.dianpinghelper.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.dianpinghelper.model.ShopInfo
import com.example.dianpinghelper.ui.theme.SuccessGreen
import com.example.dianpinghelper.ui.theme.WarningOrange
import com.example.dianpinghelper.web.DianpingWebClient
import com.example.dianpinghelper.web.WebViewState
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebScreen(
    shop: ShopInfo,
    mode: FavoriteMode,
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    totalCount: Int = 1,
    currentIndex: Int = 0,
) {
    val context = LocalContext.current
    var statusMessage by remember { mutableStateOf("准备中...") }
    var webViewState by remember { mutableStateOf(WebViewState.INIT) }
    var showManualTip by remember { mutableStateOf(false) }

    if (mode == FavoriteMode.APP) {
        LaunchedEffect(shop) {
            val opened = tryOpenDianpingApp(context, shop.name)
            if (!opened) {
                statusMessage = "大众点评 App 未安装，请使用 WebView 模式"
                showManualTip = true
            } else {
                statusMessage = "已跳转大众点评 App，请手动搜索并收藏"
                showManualTip = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = if (mode == FavoriteMode.WEBVIEW) "WebView 收藏" else "跳转收藏"
                    if (totalCount > 1) Text("$title (${currentIndex + 1}/$totalCount)")
                    else Text(title)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (webViewState == WebViewState.SUCCESS) {
                        IconButton(onClick = onSuccess) {
                            Icon(Icons.Default.Check, contentDescription = "完成")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (mode == FavoriteMode.WEBVIEW) {
                WebViewContent(
                    shop = shop,
                    onStateChange = { webViewState = it },
                    onMessage = { statusMessage = it },
                    modifier = Modifier.weight(1f),
                )
            } else {
                AppJumpContent(
                    shop = shop,
                    message = statusMessage,
                    showManualTip = showManualTip,
                    onRetryWebView = { onBack() },
                    onDone = onSuccess,
                    modifier = Modifier.weight(1f),
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = when (webViewState) {
                    WebViewState.SUCCESS -> SuccessGreen.copy(alpha = 0.1f)
                    WebViewState.ERROR, WebViewState.NEED_LOGIN -> WarningOrange.copy(alpha = 0.1f)
                    else -> MaterialTheme.colorScheme.surface
                },
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    val (icon, tint) = when (webViewState) {
                        WebViewState.LOADING, WebViewState.SEARCHING -> Icons.Default.Sync to WarningOrange
                        WebViewState.SUCCESS -> Icons.Default.CheckCircle to SuccessGreen
                        WebViewState.ERROR, WebViewState.NEED_LOGIN -> Icons.Default.Error to WarningOrange
                        else -> Icons.Default.Info to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    }
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(statusMessage, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    if (webViewState == WebViewState.SUCCESS) {
                        TextButton(onClick = onSuccess) { Text("完成") }
                    }
                }
            }
        }
    }
}

@Composable
private fun WebViewContent(
    shop: ShopInfo,
    onStateChange: (WebViewState) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val client = remember { DianpingWebClient(onStateChange, onMessage) }

    AndroidView(
        factory = { ctx -> client.createWebView(ctx) },
        modifier = modifier,
        update = { _ ->
            if (client.state == WebViewState.INIT || client.state == WebViewState.READY) {
                client.favorite(shop)
            }
        },
    )

    if (client.state == WebViewState.NEED_LOGIN) {
        Surface(modifier = Modifier.fillMaxWidth(), color = WarningOrange.copy(alpha = 0.08f)) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Login, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("请在 WebView 中登录大众点评账号，登录后自动继续", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { client.destroy() }
    }
}

@Composable
private fun AppJumpContent(
    shop: ShopInfo,
    message: String,
    showManualTip: Boolean,
    onRetryWebView: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
        Spacer(Modifier.height(16.dp))
        Text("已尝试打开大众点评", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))

        if (showManualTip) {
            Spacer(Modifier.height(16.dp))
            Card(shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("手动操作步骤：", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("1. 在大众点评中搜索「${shop.name}」", style = MaterialTheme.typography.bodySmall)
                    if (shop.address.isNotBlank()) Text("2. 确认地址: ${shop.address}", style = MaterialTheme.typography.bodySmall)
                    Text("3. 进入店铺页后点击「收藏」", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onRetryWebView) {
                    Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("改用 WebView")
                }
                Button(onClick = onDone) { Text("已完成收藏") }
            }
        }
    }
}

private fun tryOpenDianpingApp(context: Context, shopName: String): Boolean {
    val schemes = listOf(
        "dianping://search?keyword=${URLEncoder.encode(shopName, "UTF-8")}",
        "dianping://web?url=${URLEncoder.encode("https://m.dianping.com/search/city/1/keyword/${URLEncoder.encode(shopName, "UTF-8")}", "UTF-8")}",
    )
    for (scheme in schemes) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(scheme)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent); return true
            }
        } catch (_: Exception) { continue }
    }
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://m.dianping.com/search/city/1/keyword/${URLEncoder.encode(shopName, "UTF-8")}")
            `package` = "com.dianping.v1"; addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) != null) { context.startActivity(intent); return true }
    } catch (_: Exception) {}
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://m.dianping.com/search/city/1/keyword/${URLEncoder.encode(shopName, "UTF-8")}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {}
    return false
}
