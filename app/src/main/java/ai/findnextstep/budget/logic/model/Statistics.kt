package ai.findnextstep.budget.logic.model

/**
 * 单个类型在某周期内的汇总。
 */
data class CategorySummary(
    val category: Category,
    val totalAmount: Double,       // 净额（收入 - 支出）
    val incomeAmount: Double,      // 收入合计
    val expenseAmount: Double,     // 支出合计（正值）
    val transactionCount: Int
)

/**
 * 每日汇总（用于日/周/月视图中的每日行）。
 */
data class DaySummary(
    val date: String,              // yyyy-MM-dd
    val totalIncome: Double,
    val totalExpense: Double,
    val categorySummaries: List<CategorySummary>
)

/**
 * 某个周期的完整统计结果。
 */
data class PeriodStatistics(
    val period: Period,
    val startDate: String,         // yyyy-MM-dd
    val endDate: String,           // yyyy-MM-dd
    val totalIncome: Double,
    val totalExpense: Double,
    val netAmount: Double,
    val daySummaries: List<DaySummary>,
    val categorySummaries: List<CategorySummary>
)
