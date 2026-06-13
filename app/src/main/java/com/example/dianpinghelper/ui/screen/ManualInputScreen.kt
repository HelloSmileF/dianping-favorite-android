package com.example.dianpinghelper.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 手动输入页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualInputScreen(
    initialText: String,
    onConfirm: (String) -> Unit,
    onBack: () -> Unit,
) {
    var text by remember { mutableStateOf(initialText) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("输入文本") },
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
                .padding(16.dp),
        ) {
            Text(
                "请粘贴视频链接或包含店铺信息的文字",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))

            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(12.dp),
                    placeholder = {
                        Text(
                            "示例：\n" +
                            "店名：张记牛肉面 📍上海市黄浦区南京路100号\n" +
                            "或粘贴抖音/小红书/B站链接",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                    },
                )
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { onConfirm(text) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = text.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("提取店铺信息")
            }
        }
    }
}
