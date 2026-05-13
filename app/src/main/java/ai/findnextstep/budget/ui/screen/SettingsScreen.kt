package ai.findnextstep.budget.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.findnextstep.budget.ui.viewmodel.BudgetUiState
import ai.findnextstep.budget.ui.viewmodel.BudgetViewModel
import ai.findnextstep.budget.ui.viewmodel.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: BudgetViewModel,
    uiState: BudgetUiState
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // 收入输入状态
    var monthlyStr by remember { mutableStateOf(if (uiState.monthlyIncome > 0) uiState.monthlyIncome.toLong().toString() else "") }
    var dailyStr by remember { mutableStateOf(if (uiState.dailyIncome > 0) uiState.dailyIncome.toLong().toString() else "") }
    var showThemeDialog by remember { mutableStateOf(false) }

    // CSV 导入
    val csvImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.importCsv(it) }
    }

    // CSV 导出
    BackHandler(onBack = { viewModel.goBack() })

    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            try {
                val csv = viewModel.exportCsv()
                context.contentResolver.openOutputStream(it)?.use { os ->
                    os.write(csv.toByteArray())
                }
                Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.goBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 今日日薪 ──
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("今日日薪", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "¥ ${"%.2f".format(uiState.todayDailySalary)}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // ── 收入设置 ──
            Text("稳定收入设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            // 月收入
            OutlinedTextField(
                value = monthlyStr,
                onValueChange = { newVal ->
                    monthlyStr = newVal.filter { it.isDigit() }
                    val v = monthlyStr.toDoubleOrNull()
                    if (v != null) viewModel.setMonthlyIncome(v)
                },
                label = { Text("月收入") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.useDailyMode
            )

            // 日收入
            OutlinedTextField(
                value = dailyStr,
                onValueChange = { newVal ->
                    dailyStr = newVal.filter { it.isDigit() }
                    val v = dailyStr.toDoubleOrNull()
                    if (v != null) viewModel.setDailyIncome(v)
                },
                label = { Text("日收入（优先）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (uiState.useDailyMode && uiState.dailyIncome > 0) {
                Text(
                    "当前使用日收入模式：每日 ¥${"%.2f".format(uiState.dailyIncome)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            HorizontalDivider()

            // ── 主题设置 ──
            Text("外观设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Button(
                onClick = { showThemeDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("主题：${uiState.themeMode.label}")
            }

            HorizontalDivider()

            // ── 悬浮窗设置 ──
            Text("悬浮记账", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("启用悬浮窗", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "在其他应用之上显示记账气泡",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Switch(
                    checked = uiState.floatingWindowEnabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            // 检查悬浮窗权限
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                                !Settings.canDrawOverlays(context)
                            ) {
                                Toast.makeText(context, "请授予「显示在其他应用上层」权限", Toast.LENGTH_LONG).show()
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            } else {
                                viewModel.toggleFloatingWindow(true)
                            }
                        } else {
                            viewModel.toggleFloatingWindow(false)
                        }
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("隐藏提示文本", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "关闭「点击记账 · 长按关闭」提示",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Switch(
                    checked = uiState.hideHint,
                    onCheckedChange = { viewModel.setHideHint(it) }
                )
            }

            // 快捷开关提示
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("快捷开关", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "下拉通知栏 → 编辑 → 拖入「悬浮记账」磁贴即可快速开关",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            HorizontalDivider()

            // ── CSV 操作 ──
            Text("数据管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { csvImportLauncher.launch("text/*") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("导入 CSV")
                }
                Button(
                    onClick = { csvExportLauncher.launch("budget_export.csv") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("导出 CSV")
                }
            }

            HorizontalDivider()

            // ── 信息 ──
            Text(
                "共 ${uiState.transactions.size} 条记录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }

    // ── 主题选择对话框 ──
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("选择主题") },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.themeMode == mode,
                                onClick = {
                                    viewModel.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(mode.label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }
}
