package ai.findnextstep.budget.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.findnextstep.budget.logic.model.CategorySummary
import ai.findnextstep.budget.logic.model.DaySummary
import ai.findnextstep.budget.logic.model.Period
import ai.findnextstep.budget.ui.component.PeriodSelector
import ai.findnextstep.budget.ui.theme.ExpenseRed
import ai.findnextstep.budget.ui.theme.categoryBackgroundAlpha
import ai.findnextstep.budget.ui.theme.categoryForeground
import ai.findnextstep.budget.ui.theme.IncomeGreen
import ai.findnextstep.budget.ui.viewmodel.BudgetUiState
import ai.findnextstep.budget.ui.viewmodel.BudgetViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: BudgetViewModel,
    uiState: BudgetUiState
) {
    val stats = uiState.periodStatistics

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("统计") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.goBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // 周期选择
            item {
                PeriodSelector(
                    selectedPeriod = uiState.currentPeriod,
                    onPeriodSelected = { viewModel.setPeriod(it) }
                )
            }

            // 概要
            item {
                if (stats != null) {
                    SummaryHeader(stats)
                } else {
                    Box(modifier = Modifier.padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("暂无数据", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            // 类型汇总
            if (stats != null && stats.categorySummaries.isNotEmpty()) {
                item {
                    Text(
                        "类型分布",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // 找出最大支出用于比例条
                val maxExpense = stats.categorySummaries.maxOfOrNull { it.expenseAmount } ?: 1.0

                items(stats.categorySummaries) { cat ->
                    CategorySummaryRow(cat, maxExpense)
                }
            }

            // 每日明细
            if (stats != null && stats.daySummaries.isNotEmpty()) {
                item {
                    Text(
                        "每日明细",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(stats.daySummaries) { day ->
                    DaySummaryRow(day, uiState.currentPeriod)
                }
            }
        }
    }
}

@Composable
private fun SummaryHeader(stats: ai.findnextstep.budget.logic.model.PeriodStatistics) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "${stats.startDate} ~ ${stats.endDate}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("收入", style = MaterialTheme.typography.labelSmall)
                    Text(
                        "¥ ${"%.2f".format(stats.totalIncome)}",
                        color = IncomeGreen,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("支出", style = MaterialTheme.typography.labelSmall)
                    Text(
                        "¥ ${"%.2f".format(stats.totalExpense)}",
                        color = ExpenseRed,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("结余", style = MaterialTheme.typography.labelSmall)
                    Text(
                        "¥ ${"%.2f".format(stats.netAmount)}",
                        color = if (stats.netAmount >= 0) IncomeGreen else ExpenseRed,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun CategorySummaryRow(cat: CategorySummary, maxExpense: Double) {
    val fraction = if (maxExpense > 0) (cat.expenseAmount / maxExpense).toFloat() else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val catColor = categoryForeground(cat.category.key, MaterialTheme.colorScheme.primary)
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(catColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(cat.category.displayName, fontWeight = FontWeight.Medium)
                }
                Row {
                    if (cat.incomeAmount > 0) {
                        Text(
                            "+${"%.2f".format(cat.incomeAmount)} ",
                            color = IncomeGreen,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (cat.expenseAmount > 0) {
                        Text(
                            "-${"%.2f".format(cat.expenseAmount)}",
                            color = ExpenseRed,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            if (cat.expenseAmount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = ExpenseRed,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DaySummaryRow(day: DaySummary, period: Period) {
    // 提取日期展示文本
    val displayDate = if (period == Period.YEAR) {
        day.date.substring(5) // MM-dd
    } else {
        day.date.substring(if (day.date.length >= 10) 5 else 0) // MM-dd
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(displayDate, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.3f))
            // 类型标签
            if (day.categorySummaries.isNotEmpty()) {
                Row(modifier = Modifier.weight(0.4f)) {
                    day.categorySummaries.take(3).forEach { cat ->
                        val catColor = categoryForeground(cat.category.key, MaterialTheme.colorScheme.primary)
                        val bgAlpha = categoryBackgroundAlpha()
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = catColor.copy(alpha = bgAlpha),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(
                                cat.category.displayName,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                color = catColor,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    if (day.categorySummaries.size > 3) {
                        Text("…", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            // 金额
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(0.3f)) {
                if (day.totalIncome > 0) {
                    Text(
                        "+${"%.2f".format(day.totalIncome)}",
                        color = IncomeGreen,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (day.totalExpense > 0) {
                    Text(
                        "-${"%.2f".format(day.totalExpense)}",
                        color = ExpenseRed,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
