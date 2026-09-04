package ai.findnextstep.budget.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import ai.findnextstep.budget.logic.model.CategorySummary
import ai.findnextstep.budget.logic.model.DaySummary
import ai.findnextstep.budget.logic.model.Period
import ai.findnextstep.budget.logic.model.Transaction
import ai.findnextstep.budget.logic.util.dayEpochMillisRange
import ai.findnextstep.budget.ui.component.ExpenseHeatmap
import ai.findnextstep.budget.ui.component.PeriodSelector
import ai.findnextstep.budget.ui.theme.ExpenseRed
import ai.findnextstep.budget.ui.theme.categoryBackgroundAlpha
import ai.findnextstep.budget.ui.theme.categoryForeground
import ai.findnextstep.budget.ui.theme.IncomeGreen
import ai.findnextstep.budget.ui.viewmodel.BudgetUiState
import ai.findnextstep.budget.ui.viewmodel.BudgetViewModel
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: BudgetViewModel,
    uiState: BudgetUiState
) {
    val stats = uiState.periodStatistics

    BackHandler(onBack = {
        if (uiState.dayDetailDate != null) {
            viewModel.closeDayDetail()
        } else {
            viewModel.goBack()
        }
    })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("统计") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.dayDetailDate != null) {
                            viewModel.closeDayDetail()
                        } else {
                            viewModel.goBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pointerInput(uiState.currentPeriod, uiState.referenceDate) {
                    var dragX = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragX > 80f) viewModel.goToPreviousPeriod()
                            else if (dragX < -80f) viewModel.goToNextPeriod()
                            dragX = 0f
                        }
                    ) { _, dragAmount ->
                        dragX += dragAmount
                    }
                }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                PeriodSelector(
                    selectedPeriod = uiState.currentPeriod,
                    onPeriodSelected = { viewModel.setPeriod(it) }
                )

                PeriodNavigationLabel(
                    referenceDate = uiState.referenceDate,
                    period = uiState.currentPeriod
                )

                Box(modifier = Modifier.weight(1f)) {
                    val offsetX = remember { Animatable(0f) }
                    var contentWidth by remember { mutableStateOf(0f) }

                    LaunchedEffect(uiState.slideDirection) {
                        if (uiState.slideDirection != 0 && contentWidth > 0f) {
                            val startOffset = if (uiState.slideDirection < 0) -contentWidth else contentWidth
                            offsetX.snapTo(startOffset)
                            offsetX.animateTo(0f, tween(250, easing = FastOutSlowInEasing))
                            viewModel.resetSlideDirection()
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                            .onSizeChanged { contentWidth = it.width.toFloat() },
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        if (uiState.dayDetailDate != null) {
                            val detailDate = uiState.dayDetailDate
                            val date = LocalDate.parse(detailDate)
                            val (dayStart, dayEnd) = date.dayEpochMillisRange()
                            val dayTxns = uiState.transactions
                                .filter { it.timestamp in dayStart..dayEnd }
                                .sortedByDescending { it.timestamp }

                            item {
                                Text(
                                    "$detailDate 交易明细",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }

                            if (dayTxns.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("当天无交易", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }

                            items(dayTxns, key = { it.id }) { txn ->
                                TransactionSummaryRow(
                                    txn = txn,
                                    onClick = { viewModel.navigateToEditTransaction(txn) }
                                )
                            }
                        } else {
                            item {
                                if (stats != null) {
                                    SummaryHeader(stats)
                                } else {
                                    Box(
                                        modifier = Modifier.padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("暂无数据", style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            }

                            if (stats != null && stats.daySummaries.isNotEmpty() &&
                                (uiState.currentPeriod == Period.MONTH || uiState.currentPeriod == Period.YEAR)
                            ) {
                                item {
                                    ExpenseHeatmap(
                                        daySummaries = stats.daySummaries,
                                        period = uiState.currentPeriod
                                    )
                                }
                            }

                            if (stats != null && stats.categorySummaries.isNotEmpty()) {
                                item {
                                    Text(
                                        "类型分布",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }

                                item {
                                    CategoryPieChart(stats.categorySummaries)
                                }
                            }

                            if (uiState.currentPeriod == Period.DAY) {
                                val date = uiState.referenceDate
                                val (dayStart, dayEnd) = date.dayEpochMillisRange()
                                val dayTxns = uiState.transactions
                                    .filter { it.timestamp in dayStart..dayEnd }
                                    .sortedByDescending { it.timestamp }

                                if (dayTxns.isNotEmpty()) {
                                    item {
                                        Text(
                                            "当日交易",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }
                                    items(dayTxns, key = { it.id }) { txn ->
                                        TransactionSummaryRow(
                                            txn = txn,
                                            onClick = { viewModel.navigateToEditTransaction(txn) }
                                        )
                                    }
                                }
                            } else if (stats != null && stats.daySummaries.isNotEmpty()) {
                                item {
                                    Text(
                                        "每日明细",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                                items(stats.daySummaries) { day ->
                                    DaySummaryRow(
                                        day = day,
                                        period = uiState.currentPeriod,
                                        onClick = { viewModel.openDayDetail(day.date) }
                                    )
                }
            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionSummaryRow(
    txn: Transaction,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val catColor = categoryForeground(txn.category.key, if (txn.isIncome) IncomeGreen else ExpenseRed)
    val bgAlpha = categoryBackgroundAlpha()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        shape = RoundedCornerShape(6.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = catColor.copy(alpha = bgAlpha),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (txn.category.emoji.isNotEmpty()) {
                        Text(txn.category.emoji, fontSize = 16.sp)
                    } else {
                        Text(
                            txn.category.displayName.take(1),
                            color = catColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        txn.category.displayName,
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (txn.note.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            txn.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            maxLines = 1
                        )
                    }
                }
                Text(
                    dateFormat.format(Date(txn.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Text(
                (if (txn.isIncome) "+" else "-") + "¥${"%.2f".format(kotlin.math.abs(txn.amount))}",
                color = if (txn.isIncome) IncomeGreen else ExpenseRed,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium
            )
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
private fun CategoryPieChart(
    categorySummaries: List<CategorySummary>,
    modifier: Modifier = Modifier
) {
    val totalExpense = categorySummaries.sumOf { it.expenseAmount }
    if (totalExpense <= 0) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("暂无支出数据", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val sorted = categorySummaries
        .filter { it.expenseAmount > 0 }
        .sortedByDescending { it.expenseAmount }

    // categoryForeground 是 @Composable，提前计算颜色
    val categoryColors = sorted.map { cat ->
        categoryForeground(cat.category.key, MaterialTheme.colorScheme.primary)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── 环形饼图 ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                var startAngle = -90f
                Canvas(modifier = Modifier.size(180.dp)) {
                    val strokeWidth = 36.dp.toPx()
                    val halfStroke = strokeWidth / 2
                    val diameter = size.minDimension
                    val arcTopLeft = Offset(halfStroke, halfStroke)
                    val arcSize = Size(diameter - strokeWidth, diameter - strokeWidth)

                    sorted.forEachIndexed { index, cat ->
                        val sweepAngle = (cat.expenseAmount / totalExpense * 360).toFloat()
                        drawArc(
                            color = categoryColors[index],
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                        )
                        startAngle += sweepAngle
                    }
                }

                // 环形中心文字
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "总支出",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        "¥${"%.2f".format(totalExpense)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ExpenseRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── 图例 ──
            sorted.forEach { cat ->
                val percentage = (cat.expenseAmount / totalExpense * 100)
                val color = categoryForeground(cat.category.key, MaterialTheme.colorScheme.primary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(color, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            cat.category.label,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "¥${"%.2f".format(cat.expenseAmount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ExpenseRed,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Text(
                            "${"%.1f".format(percentage)}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.width(40.dp)
                        )
                }
            }
            }
        }
    }
}

@Composable
private fun PeriodNavigationLabel(
    referenceDate: LocalDate,
    period: Period
) {
    val label = formatReferenceDate(referenceDate, period)
    Text(
        label,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}

private fun formatReferenceDate(date: LocalDate, period: Period): String {
    val fmt = DateTimeFormatter.ofPattern("M月d日")
    return when (period) {
        Period.DAY -> date.format(fmt)
        Period.WEEK -> {
            val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val sunday = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            "${monday.format(fmt)} - ${sunday.format(fmt)}"
        }
        Period.MONTH -> "${date.year}年${date.monthValue}月"
        Period.YEAR -> "${date.year}年"
    }
}

@Composable
private fun DaySummaryRow(day: DaySummary, period: Period, onClick: (() -> Unit)? = null) {
    val displayDate = if (period == Period.YEAR) {
        day.date.substring(5)
    } else {
        day.date.substring(if (day.date.length >= 10) 5 else 0)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        shape = RoundedCornerShape(6.dp),
        onClick = onClick ?: {}
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
                            // 空间紧凑，只显示 emoji；自定义分类无 emoji 时显示名称
                            Text(
                                cat.category.emoji.ifEmpty { cat.category.displayName },
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
