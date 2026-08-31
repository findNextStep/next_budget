package ai.findnextstep.budget.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.findnextstep.budget.logic.model.Category
import ai.findnextstep.budget.logic.service.codingplan.CodingPlanProviders
import ai.findnextstep.budget.logic.util.dayEpochMillisRange
import ai.findnextstep.budget.ui.component.CodingPlanCard
import ai.findnextstep.budget.ui.component.CodingPlanGuideCard
import ai.findnextstep.budget.ui.theme.ExpenseRed
import ai.findnextstep.budget.ui.theme.IncomeGreen
import ai.findnextstep.budget.ui.viewmodel.BudgetUiState
import ai.findnextstep.budget.ui.viewmodel.BudgetViewModel
import ai.findnextstep.budget.ui.viewmodel.Screen
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: BudgetViewModel,
    uiState: BudgetUiState
) {
    var showQuickExpense by remember { mutableStateOf(false) }
    var quickAmount by remember { mutableStateOf("") }

    // 打开主页时自动刷新超过 24 小时未更新的 Coding Plan 用量
    LaunchedEffect(Unit) {
        viewModel.autoRefreshStaleProviders()
    }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── 整体总结余 ──
                val totalIncome = uiState.transactions.filter { it.amount > 0 }.sumOf { it.amount }
                val totalExpense = uiState.transactions.filter { it.amount < 0 }.sumOf { -it.amount }
                if (totalIncome > 0 || totalExpense > 0) {
                    item {
                        TotalBalanceCard(totalIncome, totalExpense)
                    }
                }

                // ── 本日概览 ──
                item {
                    TodaySummaryCard(uiState)
                }

                // ── Coding Plan 用量 ──
                CodingPlanProviders.all.forEach { provider ->
                    val key = uiState.codingPlanApiKeys[provider.id].orEmpty()
                    if (key.isNotEmpty()) {
                        item(key = "coding_plan_${provider.id}") {
                            val state = uiState.codingPlanStates[provider.id]
                            CodingPlanCard(
                                title = provider.displayName,
                                usage = state?.snapshot,
                                loading = state?.loading == true,
                                error = state?.error,
                                balanceProgressMax = uiState.balanceProgressMax,
                                onRefresh = { viewModel.refreshProviderUsage(provider.id) }
                            )
                        }
                    }
                }
                if (uiState.codingPlanApiKeys.values.all { it.isEmpty() } && !uiState.codingPlanGuideDismissed) {
                    item(key = "coding_plan_guide") {
                        CodingPlanGuideCard(
                            onGoSettings = { viewModel.navigateTo(Screen.SETTINGS) },
                            onDismiss = { viewModel.dismissCodingPlanGuide() }
                        )
                    }
                }
            }

            // ── 底部操作区 ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    LargeActionButton(
                        label = "− 支出",
                        color = ExpenseRed,
                        onClick = { viewModel.navigateTo(Screen.ADD_EXPENSE) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    LargeActionButton(
                        label = "长期支出",
                        color = ExpenseRed.copy(alpha = 0.8f),
                        onClick = { viewModel.navigateTo(Screen.AMORTIZE_EXPENSE) },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    LargeActionButton(
                        label = "+ 收入",
                        color = IncomeGreen,
                        onClick = { viewModel.navigateTo(Screen.ADD_INCOME) },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedButton(
                    onClick = { viewModel.navigateTo(Screen.STATISTICS) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("查看统计")
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
private fun TotalBalanceCard(totalIncome: Double, totalExpense: Double) {
    val netAmount = totalIncome - totalExpense
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "总结余",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "¥ ${"%.2f".format(netAmount)}",
                color = if (netAmount >= 0) IncomeGreen else ExpenseRed,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall
            )
        }
    }
}

@Composable
private fun TodaySummaryCard(uiState: BudgetUiState) {
    val (todayStart, todayEnd) = LocalDate.now().dayEpochMillisRange()
    val todayTxns = uiState.transactions.filter { it.timestamp in todayStart..todayEnd }
    val income = todayTxns.filter { it.amount > 0 }.sumOf { it.amount }
    val expense = todayTxns.filter { it.amount < 0 }.sumOf { -it.amount }
    val net = income - expense

    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("本日概览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                SummaryItem("收入", income, IncomeGreen)
                SummaryItem("支出", expense, ExpenseRed)
                SummaryItem("结余", net,
                    if (net >= 0) IncomeGreen else ExpenseRed
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
                        "推断类型: ${predictedCategory.label}",
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
