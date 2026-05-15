package ai.findnextstep.budget.ui.component

import ai.findnextstep.budget.logic.model.DaySummary
import ai.findnextstep.budget.logic.model.Period
import ai.findnextstep.budget.ui.theme.ExpenseRed
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun ExpenseHeatmap(
    daySummaries: List<DaySummary>,
    period: Period,
    modifier: Modifier = Modifier
) {
    if (daySummaries.isEmpty()) return

    when (period) {
        Period.MONTH -> {
            val firstDate = LocalDate.parse(daySummaries.first().date)
            MonthHeatmap(daySummaries, YearMonth.from(firstDate), modifier)
        }
        Period.YEAR -> YearHeatmap(daySummaries, modifier)
        else -> {}
    }
}

@Composable
private fun MonthHeatmap(
    daySummaries: List<DaySummary>,
    yearMonth: YearMonth,
    modifier: Modifier
) {
    val dateExpenseMap = daySummaries.associate { it.date to it.totalExpense }
    val maxExpense = daySummaries.maxOfOrNull { it.totalExpense } ?: 0.0
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val firstDay = yearMonth.atDay(1)
    val daysInMonth = yearMonth.lengthOfMonth()
    val firstDayOffset = firstDay.dayOfWeek.value - 1
    val totalWeeks = (daysInMonth + firstDayOffset + 6) / 7
    val dayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "${yearMonth.monthValue}月",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Row(modifier = Modifier.fillMaxWidth().padding(start = 2.dp)) {
                dayLabels.forEachIndexed { i, label ->
                    Text(
                        label,
                        fontSize = 10.sp,
                        modifier = Modifier.size(14.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    if (i < 6) Spacer(modifier = Modifier.width(2.dp))
                }
            }
            Spacer(modifier = Modifier.height(2.dp))

            for (week in 0 until totalWeeks) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (dow in 0..6) {
                        val dayNum = week * 7 + dow - firstDayOffset + 1
                        if (dayNum in 1..daysInMonth) {
                            val date = firstDay.withDayOfMonth(dayNum).toString()
                            val expense = dateExpenseMap[date] ?: 0.0
                            HeatmapCell(expense, maxExpense, emptyColor, 14.dp)
                        } else {
                            Box(modifier = Modifier.size(14.dp))
                        }
                        if (dow < 6) Spacer(modifier = Modifier.width(2.dp))
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
            }

            Spacer(modifier = Modifier.height(4.dp))
            HeatmapLegend(maxExpense, emptyColor)
        }
    }
}

@Composable
private fun YearHeatmap(
    daySummaries: List<DaySummary>,
    modifier: Modifier
) {
    val weekExpenses = daySummaries
        .groupBy { (LocalDate.parse(it.date).dayOfYear - 1) / 7 }
        .mapValues { (_, days) -> days.sumOf { it.totalExpense } }
        .toList()
        .sortedBy { it.first }

    val maxExpense = weekExpenses.maxOfOrNull { it.second } ?: 0.0
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val firstDate = LocalDate.parse(daySummaries.first().date)

    val cols = 13
    val rows = (weekExpenses.size + cols - 1) / cols

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "${firstDate.year}年",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            for (r in 0 until rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    for (c in 0 until cols) {
                        val idx = r * cols + c
                        if (idx < weekExpenses.size) {
                            val (_, expense) = weekExpenses[idx]
                            HeatmapCell(expense, maxExpense, emptyColor, 14.dp)
                        } else {
                            Spacer(modifier = Modifier.size(14.dp))
                        }
                        if (c < cols - 1) {
                            Spacer(modifier = Modifier.width(2.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
            }

            Spacer(modifier = Modifier.height(4.dp))
            HeatmapLegend(maxExpense, emptyColor)
        }
    }
}

@Composable
private fun HeatmapCell(
    expense: Double,
    maxExpense: Double,
    emptyColor: Color,
    size: androidx.compose.ui.unit.Dp
) {
    val color = if (maxExpense > 0 && expense > 0) {
        lerp(emptyColor, ExpenseRed, (expense / maxExpense).toFloat().coerceIn(0f, 1f))
    } else {
        emptyColor
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
    )
}

@Composable
private fun HeatmapLegend(maxExpense: Double, emptyColor: Color) {
    if (maxExpense <= 0) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("少", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.width(4.dp))
        for (level in 0..4) {
            val ratio = level / 4f
            val color = lerp(emptyColor, ExpenseRed, ratio)
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color)
            )
            Spacer(modifier = Modifier.width(2.dp))
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text("多", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
}
