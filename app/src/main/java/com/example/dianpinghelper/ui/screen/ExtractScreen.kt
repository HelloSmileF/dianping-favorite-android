package com.example.dianpinghelper.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dianpinghelper.extractor.ShopInfoExtractor
import com.example.dianpinghelper.model.ShopInfo
import com.example.dianpinghelper.ui.theme.SuccessGreen
import com.example.dianpinghelper.ui.theme.WarningOrange

/**
 * 提取结果确认页
 * 支持多店铺选择
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractScreen(
    initialText: String,
    onConfirm: (List<ShopInfo>, mode: FavoriteMode) -> Unit,
    onBack: () -> Unit,
) {
    // 提取所有店铺
    val allShops = remember { mutableStateListOf<ShopInfo>().apply {
        addAll(ShopInfoExtractor.extractAll(initialText))
    } }
    // 每个店铺的勾选状态
    val checked = remember { mutableStateListOf<Boolean>().apply {
        repeat(allShops.size) { add(true) }
    } }
    var selectedMode by remember { mutableStateOf(FavoriteMode.WEBVIEW) }
    var showManualInput by remember { mutableStateOf(allShops.isEmpty()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (allShops.isEmpty()) "确认店铺信息"
                         else "找到 ${allShops.size} 个店铺")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── 未提取到信息 → 手动输入 ──
            if (allShops.isEmpty() && !showManualInput) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SearchOff, contentDescription = null,
                            modifier = Modifier.size(64.dp), tint = WarningOrange)
                        Spacer(Modifier.height(12.dp))
                        Text("未自动提取到店铺信息", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text("请手动输入", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { showManualInput = true }) { Text("手动输入") }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onBack) { Text("返回修改文本") }
                    }
                }
                return@Scaffold
            }

            // ── 手动输入 ──
            if (allShops.isEmpty()) {
                var manualName by remember { mutableStateOf("") }
                var manualAddr by remember { mutableStateOf("") }

                OutlinedTextField(value = manualName, onValueChange = { manualName = it },
                    label = { Text("店铺名称 *") }, modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Store, contentDescription = null) },
                    singleLine = true, shape = RoundedCornerShape(12.dp))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = manualAddr, onValueChange = { manualAddr = it },
                    label = { Text("地址（可选）") }, modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                    singleLine = true, shape = RoundedCornerShape(12.dp))
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (manualName.isNotBlank()) {
                            onConfirm(listOf(ShopInfo(name = manualName.trim(), address = manualAddr.trim())), selectedMode)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = manualName.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("加入收藏夹") }
                return@Scaffold
            }

            // ── 店铺列表 ──
            Card(shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp)) {
                    // 全选/取消
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("选择要收藏的店铺", fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            val allChecked = checked.all { it }
                            for (i in checked.indices) checked[i] = !allChecked
                        }) {
                            Text(if (checked.all { it }) "取消全选" else "全选")
                        }
                    }
                    Divider()
                    // 每个店铺
                    allShops.forEachIndexed { idx, shop ->
                        Row(modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = checked[idx],
                                onCheckedChange = { checked[idx] = it })
                            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(shop.name, fontWeight = FontWeight.Medium)
                                if (shop.address.isNotBlank()) {
                                    Text(shop.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                            }
                        }
                        if (idx < allShops.lastIndex) Divider()
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── 收藏方式 ──
            Text("收藏方式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            ModeCard(selected = selectedMode == FavoriteMode.WEBVIEW,
                onClick = { selectedMode = FavoriteMode.WEBVIEW },
                icon = Icons.Default.Public, title = "WebView 自动收藏",
                description = "在 App 内打开大众点评网页版，自动搜索并收藏", badge = "推荐")
            Spacer(Modifier.height(6.dp))
            ModeCard(selected = selectedMode == FavoriteMode.APP,
                onClick = { selectedMode = FavoriteMode.APP },
                icon = Icons.Default.OpenInNew, title = "跳转大众点评 App",
                description = "跳转到大众点评 App 搜索，需手动收藏")

            Spacer(Modifier.height(20.dp))

            // ── 确认按钮 ──
            val selectedCount = checked.count { it }
            Button(
                onClick = {
                    val selected = allShops.filterIndexed { i, _ -> checked[i] }
                    if (selected.isNotEmpty()) onConfirm(selected, selectedMode)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = selectedCount > 0,
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.Favorite, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("收藏选中的 $selectedCount 个店铺")
            }
        }
    }
}

@Composable
private fun ModeCard(
    selected: Boolean, onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String, description: String, badge: String? = null,
) {
    Card(onClick = onClick, shape = RoundedCornerShape(12.dp),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(Modifier.width(8.dp))
            Icon(icon, contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Medium)
                    if (badge != null) {
                        Spacer(Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(4.dp),
                            color = SuccessGreen.copy(alpha = 0.15f)) {
                            Text(badge, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = SuccessGreen, fontSize = MaterialTheme.typography.bodySmall.fontSize * 0.85f)
                        }
                    }
                }
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}

enum class FavoriteMode {
    WEBVIEW, APP,
}
