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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 主页 Coding Plan 用量卡片。
 * 未配置 Key 且引导未关闭时显示配置引导；已配置时显示用量与手动刷新。
 */
@Composable
fun CodingPlanCard(
    apiKey: String,
    usage: UsageSnapshot?,
    loading: Boolean,
    error: String?,
    guideDismissed: Boolean,
    onRefresh: () -> Unit,
    onDismissGuide: () -> Unit,
    onGoSettings: () -> Unit
) {
    if (apiKey.isEmpty()) {
        if (!guideDismissed) {
            GuideCard(onGoSettings = onGoSettings, onDismiss = onDismissGuide)
        }
        return
    }

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
                Text("Kimi Coding Plan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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

@Composable
private fun GuideCard(onGoSettings: () -> Unit, onDismiss: () -> Unit) {
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
                "配置 Kimi Code API Key 后，可在此查看 Coding Plan 订阅用量",
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
        Text(
            "${(percent * 100).toInt()}%（${formatCount(limit.used)}/${formatCount(limit.limit)}）",
            style = MaterialTheme.typography.bodyMedium
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
    LinearProgressIndicator(
        progress = { percent },
        modifier = Modifier.fillMaxWidth(),
        color = if (percent < 0.6f) IncomeGreen else ExpenseRed
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
