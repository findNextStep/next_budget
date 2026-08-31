package ai.findnextstep.budget.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

// ── 亮色主题 ──
val LightPrimary = Color(0xFF1976D2)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightBackground = Color(0xFFFAFAFA)
val LightSurface = Color(0xFFFFFFFF)
val LightOnBackground = Color(0xFF1C1B1F)
val LightOnSurface = Color(0xFF1C1B1F)
val LightError = Color(0xFFBA1A1A)

// ── 暗色主题 ──
val DarkPrimary = Color(0xFF90CAF9)
val DarkOnPrimary = Color(0xFF0D47A1)
val DarkBackground = Color(0xFF1C1B1F)
val DarkSurface = Color(0xFF2C2C2E)
val DarkOnBackground = Color(0xFFE6E1E5)
val DarkOnSurface = Color(0xFFE6E1E5)
val DarkError = Color(0xFFFFB4AB)

// ── OLED 纯黑主题 ──
val BlackPrimary = Color(0xFFBB86FC)
val BlackOnPrimary = Color(0xFF000000)
val BlackBackground = Color(0xFF000000)
val BlackSurface = Color(0xFF0A0A0A)
val BlackOnBackground = Color(0xFFE0E0E0)
val BlackOnSurface = Color(0xFFE0E0E0)
val BlackError = Color(0xFFCF6679)

// ── 通用 ──
val IncomeGreen = Color(0xFF4CAF50)
val ExpenseRed = Color(0xFFF44336)
// ── 分类颜色 ──
// 按用途区分，语义化配色
val CategorySalaries = Color(0xFF2E7D32)       // 薪水 — 稳重深绿
val CategoryAutomatic = Color(0xFF00796B)      // 自动入账 — 青色
val CategoryBonus = Color(0xFFE65100)          // 奖金 — 橙色（醒目奖励）
val CategoryInvestment = Color(0xFF1565C0)     // 投资 — 蓝色（信赖）
val CategoryRefund = Color(0xFF558B2F)         // 退款 — 浅绿

val CategoryFood = Color(0xFFBF360C)           // 主食 — 深橙红
val CategorySnack = Color(0xFF8D6E63)          // 零食 — 棕色
val CategoryRent = Color(0xFFC62828)           // 房租 — 深红（大额支出）
val CategoryTraffic = Color(0xFF283593)        // 交通 — 靛蓝
val CategoryShopping = Color(0xFF6A1B9A)       // 购物 — 紫色
val CategoryParty = Color(0xFFAD1457)          // 聚会 — 粉色
val CategoryGift = Color(0xFFC2185B)           // 礼品 — 玫红
val CategoryDonation = Color(0xFF4527A0)       // 捐赠 — 深紫
val CategoryEntertainment = Color(0xFF00838F)   // 娱乐 — 暗青
val CategoryMedical = Color(0xFFD32F2F)        // 医疗 — 红色
val CategoryEducation = Color(0xFF00695C)      // 教育 — 墨绿
val CategoryUtilities = Color(0xFF546E7A)      // 水电 — 灰蓝
val CategoryOther = Color(0xFF757575)          // 其他 — 灰色

/** 分类 key → 颜色的映射 */
val CategoryColorMap: Map<String, Color> = mapOf(
    "SALARY" to CategorySalaries,
    "AUTOMATIC" to CategoryAutomatic,
    "BONUS" to CategoryBonus,
    "INVESTMENT" to CategoryInvestment,
    "REFUND" to CategoryRefund,
    "FOOD" to CategoryFood,
    "SNACK" to CategorySnack,
    "RENT" to CategoryRent,
    "TRAFFIC" to CategoryTraffic,
    "SHOPPING" to CategoryShopping,
    "PARTY" to CategoryParty,
    "GIFT" to CategoryGift,
    "DONATION" to CategoryDonation,
    "ENTERTAINMENT" to CategoryEntertainment,
    "MEDICAL" to CategoryMedical,
    "EDUCATION" to CategoryEducation,
    "UTILITIES" to CategoryUtilities,
    "OTHER" to CategoryOther
)

/**
 * 返回分类对应的前景色（文字/图标）。
 * 深色主题下自动向白色提亮 25%，保证可读性。
 */
@Composable
fun categoryForeground(key: String, fallback: Color): Color {
    val base = CategoryColorMap[key] ?: fallback
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) androidx.compose.ui.graphics.lerp(base, Color.White, 0.30f) else base
}

/**
 * 返回分类背景色的 alpha 值。
 * 深色主题下使用更高透明度，使淡色底在暗背景上可见。
 */
@Composable
fun categoryBackgroundAlpha(): Float {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) 0.22f else 0.12f
}

val ChartColors = listOf(
    Color(0xFF2196F3), Color(0xFF4CAF50), Color(0xFFFF9800),
    Color(0xFF9C27B0), Color(0xFF00BCD4), Color(0xFFFF5722),
    Color(0xFF795548), Color(0xFF607D8B), Color(0xFFE91E63),
    Color(0xFF3F51B5)
)
