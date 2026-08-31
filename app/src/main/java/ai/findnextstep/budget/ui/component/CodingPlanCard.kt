package ai.findnextstep.budget.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.findnextstep.budget.logic.model.UsageLimit
import ai.findnextstep.budget.logic.model.UsageSnapshot
import ai.findnextstep.budget.ui.theme.ExpenseRed
import ai.findnextstep.budget.ui.theme.IncomeGreen
import ai.findnextstep.budget.ui.theme.WarningYellow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 主页 Coding Plan 用量卡片（单个平台）。
 * 由外层判断何时展示：平台已配置 API Key 时展示。
 */
@Composable
fun CodingPlanCard(
    title: String,
    usage: UsageSnapshot?,
    loading: Boolean,
    error: String?,
    balanceProgressMax: Double,
    onRefresh: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新", modifier = Modifier.size(20.dp))
                    }
                }
            }

            if (usage != null) {
                usage.summary?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    UsageRow(it)
                }
                usage.limits.forEach { limit ->
                    Spacer(modifier = Modifier.height(8.dp))
                    UsageRow(limit)
                }
                usage.balances.forEach { balance ->
                    Spacer(modifier = Modifier.height(8.dp))
                    BalanceRow(balance.currency, balance.total, balance.granted, balanceProgressMax)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "上次刷新 ${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(usage.fetchedAtMillis))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            } else if (!loading && error == null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "点击右上角刷新查询用量",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(error, style = MaterialTheme.typography.bodySmall, color = ExpenseRed)
            }
        }
    }
}

/** 未配置任何平台 Key 时的引导卡片，可关闭 */
@Composable
fun CodingPlanGuideCard(onGoSettings: () -> Unit, onDismiss: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Coding Plan 用量", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", modifier = Modifier.size(18.dp))
                }
            }
            Text(
                "配置 Kimi Code / GLM / DeepSeek 的 API Key 后，可在此查看各平台用量与余额",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onGoSettings, modifier = Modifier.fillMaxWidth()) {
                Text("去设置")
            }
        }
    }
}

@Composable
private fun UsageRow(limit: UsageLimit) {
    val percent = limit.percent
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(limit.label, style = MaterialTheme.typography.bodyMedium)
        val valueText = if (limit.limit > 0) {
            "${(percent * 100).toInt()}%（${formatCount(limit.used)}/${formatCount(limit.limit)}）"
        } else {
            "${(percent * 100).toInt()}%"
        }
        Text(valueText, style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(modifier = Modifier.height(4.dp))
    LinearProgressIndicator(
        progress = { percent },
        modifier = Modifier.fillMaxWidth(),
        color = usageProgressColor(percent)
    )
    limit.resetAtMillis?.let { resetAt ->
        val diffMillis = resetAt - System.currentTimeMillis()
        if (diffMillis > 0) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "${formatCountdown(diffMillis)}后重置",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun BalanceRow(currency: String, total: String, granted: String?, balanceProgressMax: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("余额（$currency）", style = MaterialTheme.typography.bodyMedium)
        Text(
            if (granted != null && granted != "0.00" && granted != "0")
                "$total（含赠金 $granted）"
            else
                total,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
    // 余额低于上限时展示剩余进度条，充足（≥上限）时仅显示数字
    val amount = total.toDoubleOrNull()
    if (amount != null && balanceProgressMax > 0 && amount < balanceProgressMax) {
        val remaining = (amount / balanceProgressMax).toFloat().coerceIn(0f, 1f)
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { remaining },
            modifier = Modifier.fillMaxWidth(),
            color = usageProgressColor(1f - remaining)
        )
    }
}

/** 用量进度条三段配色：<60% 绿，60%~90% 黄，≥90%（剩余<10%）红 */
private fun usageProgressColor(usedFraction: Float): androidx.compose.ui.graphics.Color = when {
    usedFraction < 0.6f -> IncomeGreen
    usedFraction < 0.9f -> WarningYellow
    else -> ExpenseRed
}

private fun formatCount(count: Long): String = when {
    count >= 1_000_000_000L -> "%.1fB".format(count / 1e9)
    count >= 1_000_000L -> "%.1fM".format(count / 1e6)
    count >= 1_000L -> "%.1fK".format(count / 1e3)
    else -> count.toString()
}

private fun formatCountdown(diffMillis: Long): String {
    val totalMinutes = diffMillis / 60_000
    val days = totalMinutes / (24 * 60)
    val hours = (totalMinutes % (24 * 60)) / 60
    val minutes = totalMinutes % 60
    return buildString {
        if (days > 0) append("${days}天")
        if (hours > 0) append("${hours}小时")
        if (days == 0L) append("${minutes}分钟")
    }
}
