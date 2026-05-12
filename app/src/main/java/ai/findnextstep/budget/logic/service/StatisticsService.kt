package ai.findnextstep.budget.logic.service

import ai.findnextstep.budget.logic.model.*
import ai.findnextstep.budget.logic.repository.TransactionRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * 统计分析服务。提供日/周/月/年维度的收支统计。
 * 纯逻辑层，不依赖 Android。
 */
class StatisticsService(private val repository: TransactionRepository) {

    /**
     * 按指定周期统计。
     * @param date 参考日期（用于确定周期范围）
     */
    fun getStatistics(date: LocalDate, period: Period): PeriodStatistics {
        val range = dateRangeForPeriod(date, period)
        val transactions = repository.getByTimeRange(range.first, range.second)
        return buildStatistics(transactions, period, range.first, range.second)
    }

    /**
     * 计算给定日期的周期起止时间范围。
     */
    private fun dateRangeForPeriod(date: LocalDate, period: Period): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val startDate: LocalDate
        val endDate: LocalDate

        when (period) {
            Period.DAY -> {
                startDate = date
                endDate = date
            }
            Period.WEEK -> {
                startDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                endDate = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            }
            Period.MONTH -> {
                startDate = date.withDayOfMonth(1)
                endDate = date.withDayOfMonth(date.lengthOfMonth())
            }
            Period.YEAR -> {
                startDate = date.withDayOfYear(1)
                endDate = date.withDayOfYear(date.lengthOfYear())
            }
        }

        val startMillis = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return Pair(startMillis, endMillis)
    }

    private fun buildStatistics(
        transactions: List<Transaction>,
        period: Period,
        startMillis: Long,
        endMillis: Long
    ): PeriodStatistics {
        val zone = ZoneId.systemDefault()
        val startDate = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(startMillis), zone)
        val endDate = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(endMillis), zone)

        // 按日期分组
        val byDate: Map<LocalDate, List<Transaction>> = transactions.groupBy {
            LocalDate.ofInstant(java.time.Instant.ofEpochMilli(it.timestamp), zone)
        }

        // 构建每日汇总
        val daySummaries = mutableListOf<DaySummary>()
        var current = startDate
        while (!current.isAfter(endDate)) {
            val dayTxns = byDate[current] ?: emptyList()
            val daySummary = buildDaySummary(current, dayTxns)
            daySummaries.add(daySummary)
            current = current.plusDays(1)
        }

        // 全局按类型汇总
        val categorySummaries = transactions
            .groupBy { it.category }
            .map { (cat, txns) ->
                val income = txns.filter { it.amount > 0 }.sumOf { it.amount }
                val expense = txns.filter { it.amount < 0 }.sumOf { -it.amount }
                CategorySummary(
                    category = cat,
                    totalAmount = income - expense,
                    incomeAmount = income,
                    expenseAmount = expense,
                    transactionCount = txns.size
                )
            }
            .sortedByDescending { it.expenseAmount }

        val totalIncome = transactions.filter { it.amount > 0 }.sumOf { it.amount }
        val totalExpense = transactions.filter { it.amount < 0 }.sumOf { -it.amount }

        return PeriodStatistics(
            period = period,
            startDate = startDate.toString(),
            endDate = endDate.toString(),
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            netAmount = totalIncome - totalExpense,
            daySummaries = daySummaries,
            categorySummaries = categorySummaries
        )
    }

    private fun buildDaySummary(date: LocalDate, transactions: List<Transaction>): DaySummary {
        val income = transactions.filter { it.amount > 0 }.sumOf { it.amount }
        val expense = transactions.filter { it.amount < 0 }.sumOf { -it.amount }
        val catSummaries = transactions
            .groupBy { it.category }
            .map { (cat, txns) ->
                val inc = txns.filter { it.amount > 0 }.sumOf { it.amount }
                val exp = txns.filter { it.amount < 0 }.sumOf { -it.amount }
                CategorySummary(cat, inc - exp, inc, exp, txns.size)
            }
            .sortedByDescending { it.expenseAmount }
        return DaySummary(date.toString(), income, expense, catSummaries)
    }
}
