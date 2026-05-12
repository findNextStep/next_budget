package ai.findnextstep.budget.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 数字输入器。类似计算器布局。
 * @param value 当前输入值（字符串）
 * @param onValueChange 值变更回调
 * @param onConfirm 确认按钮（OK）回调
 * @param showDecimal 是否显示小数点按钮
 */
@Composable
fun NumberPad(
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    showDecimal: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 显示区域
        Text(
            text = value.ifEmpty { "0" },
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        )

        // 数字按钮区
        val buttons = listOf(
            listOf("7", "8", "9", "⌫"),
            listOf("4", "5", "6", "C"),
            listOf("1", "2", "3", "OK"),
            listOf(if (showDecimal) "." else "", "0", "00", "")
        )

        buttons.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { label ->
                    if (label.isEmpty()) {
                        Spacer(modifier = Modifier.size(72.dp))
                    } else {
                        NumberPadButton(
                            label = label,
                            onClick = {
                                when (label) {
                                    "⌫" -> if (value.isNotEmpty()) onValueChange(value.dropLast(1))
                                    "C" -> onValueChange("")
                                    "OK" -> onConfirm()
                                    "." -> if (!value.contains(".")) onValueChange(value + ".")
                                    else -> onValueChange(value + label)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberPadButton(
    label: String,
    onClick: () -> Unit
) {
    val isAction = label in listOf("⌫", "C", "OK")
    val containerColor = when (label) {
        "OK" -> MaterialTheme.colorScheme.primary
        "⌫", "C" -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (label) {
        "OK" -> MaterialTheme.colorScheme.onPrimary
        "⌫", "C" -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Button(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        modifier = Modifier
            .size(72.dp)
            .padding(4.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = label,
            fontSize = if (isAction) 16.sp else 22.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
