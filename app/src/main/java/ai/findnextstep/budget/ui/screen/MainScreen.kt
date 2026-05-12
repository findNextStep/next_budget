package ai.findnextstep.budget.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.findnextstep.budget.logic.model.Category
import ai.findnextstep.budget.logic.model.Transaction
import ai.findnextstep.budget.ui.theme.ExpenseRed
import ai.findnextstep.budget.ui.theme.categoryBackgroundAlpha
import ai.findnextstep.budget.ui.theme.categoryForeground
import ai.findnextstep.budget.ui.theme.IncomeGreen
import ai.findnextstep.budget.ui.viewmodel.BudgetUiState
import ai.findnextstep.budget.ui.viewmodel.BudgetViewModel
import ai.findnextstep.budget.ui.viewmodel.Screen
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: BudgetViewModel,
    uiState: BudgetUiState
) {
    var showQuickExpense by remember { mutableStateOf(false) }
    var quickAmount by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Budget") },
                actions = {
                    IconButton(onClick = { viewModel.navigateTo(Screen.SETTINGS) }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        floatingActionButton = {
            // 浮动快速记账按钮
            FloatingActionButton(
                onClick = { showQuickExpense = true },
                containerColor = ExpenseRed,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Edit, contentDescription = "快速记账", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 日薪信息 ──
            if (uiState.todayDailySalary > 0) {
                item {
                    DailySalaryCard(uiState.todayDailySalary)
                }
            }

            // ── 悬浮窗快捷开关 ──
            item {
                FloatingWindowToggleCard(
                    enabled = uiState.floatingWindowEnabled,
                    onToggle = { viewModel.toggleFloatingWindow(it) }
                )
            }

            // ── 本月概要 ──
            item {
                MonthSummaryCard(uiState)
            }

            // ── +/- 按钮区 ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // 记支出
                    LargeActionButton(
                        label = "− 支出",
                        color = ExpenseRed,
                        onClick = { viewModel.navigateTo(Screen.ADD_EXPENSE) },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    // 记收入
                    LargeActionButton(
                        label = "+ 收入",
                        color = IncomeGreen,
                        onClick = { viewModel.navigateTo(Screen.ADD_INCOME) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── 统计入口 ──
            item {
                OutlinedButton(
                    onClick = { viewModel.navigateTo(Screen.STATISTICS) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("查看统计")
                }
            }

            // ── 最近记录 ──
            if (uiState.recentTransactions.isNotEmpty()) {
                item {
                    Text(
                        "最近记录",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(uiState.recentTransactions.take(10)) { txn ->
                    TransactionItem(txn = txn, onDelete = { viewModel.deleteTransaction(txn.id) })
                }
            }
        }
    }

    // ── 快速记账浮动窗口 ──
    if (showQuickExpense) {
        QuickExpenseDialog(
            amount = quickAmount,
            onAmountChange = { newVal ->
                quickAmount = newVal
                // 尝试自动推断类型
                val amt = newVal.toDoubleOrNull()
                if (amt != null && amt > 0) {
                    viewModel.predictFloatingCategory(amt)
                }
            },
            predictedCategory = uiState.floatingCategory,
            onDismiss = {
                showQuickExpense = false
                quickAmount = ""
                viewModel.toggleFloatingInput(false)
            },
            onConfirm = {
                val amt = quickAmount.toDoubleOrNull()
                if (amt != null && amt > 0) {
                    viewModel.submitFloatingExpense(amt)
                }
                showQuickExpense = false
                quickAmount = ""
            }
        )
    }
}

@Composable
private fun DailySalaryCard(dailySalary: Double) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("今日日薪", style = MaterialTheme.typography.labelMedium)
                Text(
                    "¥ ${"%.2f".format(dailySalary)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MonthSummaryCard(uiState: BudgetUiState) {
    val stats = uiState.periodStatistics
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("本月概览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                SummaryItem("收入", stats?.totalIncome ?: 0.0, IncomeGreen)
                SummaryItem("支出", stats?.totalExpense ?: 0.0, ExpenseRed)
                SummaryItem("结余", stats?.netAmount ?: 0.0,
                    if ((stats?.netAmount ?: 0.0) >= 0) IncomeGreen else ExpenseRed
                )
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, amount: Double, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(
            "¥ ${"%.2f".format(kotlin.math.abs(amount))}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun LargeActionButton(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
fun TransactionItem(txn: Transaction, onDelete: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 类型图标
            val catColor = categoryForeground(txn.category.key, if (txn.isIncome) IncomeGreen else ExpenseRed)
            val bgAlpha = categoryBackgroundAlpha()
            Surface(
                shape = CircleShape,
                color = catColor.copy(alpha = bgAlpha),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        txn.category.displayName.take(1),
                        color = catColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(txn.category.displayName, fontWeight = FontWeight.Medium)
                Text(
                    dateFormat.format(Date(txn.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Text(
                (if (txn.isIncome) "+" else "-") + "¥${"%.2f".format(kotlin.math.abs(txn.amount))}",
                color = if (txn.isIncome) IncomeGreen else ExpenseRed,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun FloatingWindowToggleCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled)
                ExpenseRed.copy(alpha = 0.10f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = if (enabled) ExpenseRed else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "悬浮记账",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    if (enabled) "已开启 · 点击气泡记账" else "在其他应用上快速记账",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    if (checked) {
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
                            onToggle(true)
                        }
                    } else {
                        onToggle(false)
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickExpenseDialog(
    amount: String,
    onAmountChange: (String) -> Unit,
    predictedCategory: Category?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("快速记账") },
        text = {
            Column {
                OutlinedTextField(
                    value = amount,
                    onValueChange = onAmountChange,
                    label = { Text("金额") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (predictedCategory != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "推断类型: ${predictedCategory.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = (amount.toDoubleOrNull() ?: 0.0) > 0
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
