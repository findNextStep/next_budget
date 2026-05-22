package ai.findnextstep.budget.logic.service

import ai.findnextstep.budget.logic.model.Category
import ai.findnextstep.budget.logic.model.Transaction
import ai.findnextstep.budget.logic.repository.TransactionRepository
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * 稳定收入模块。
 * 根据月收入按当月天数折算日薪，每日 0:00 自动入账。
 * 若对应时间 app 未打开，在下次调用 [processPendingDeposits] 时补录。
 */
class SalaryService(private val repository: TransactionRepository) {

    /** 月收入（可由用户设置） */
    var monthlyIncome: Double = 0.0
        private set

    /** 上次自动入账日期（存储为 yyyy-MM-dd） */
    var lastDepositDate: String? = null
        private set

    /** 日薪模式：true=直接使用日薪，false=从月收入折算 */
    var useDailyMode: Boolean = false
        private set

    /** 直接设置的日收入 */
    var dailyIncome: Double = 0.0
        private set

    fun setMonthlyIncome(income: Double) {
        monthlyIncome = income
        useDailyMode = false
    }

    fun setDailyIncome(income: Double) {
        dailyIncome = income
        useDailyMode = true
    }

    /**
     * 计算指定月份的日薪。
     */
    fun getDailySalary(year: Int, month: Int): Double {
        return if (useDailyMode) {
            dailyIncome
        } else {
            val daysInMonth = YearMonth.of(year, month).lengthOfMonth()
            if (daysInMonth > 0) monthlyIncome / daysInMonth else 0.0
        }
    }

    /**
     * 计算今天的日薪。
     */
    fun getTodayDailySalary(): Double {
        val today = LocalDate.now()
        return getDailySalary(today.year, today.monthValue)
    }

    /**
     * 处理待入账项。
     * 从上次入账日期的次日开始，到昨天为止，
     * 对每一天检查是否已过 0:00，若是则补录日薪。
     *
     * 调用时机：app 启动或从后台恢复时。
     */
    fun processPendingDeposits(): List<Transaction> {
        val today = LocalDate.now()
        val newDeposits = mutableListOf<Transaction>()

        // 确定检查起始日期
        val startCheckDate: LocalDate = if (lastDepositDate != null) {
            try {
                LocalDate.parse(lastDepositDate).plusDays(1)
            } catch (_: Exception) {
                today
            }
        } else {
            // 首次使用：从当天开始
            today
        }

        val now = LocalTime.now()
        val depositTime = LocalTime.of(0, 0)

        var checkDate = startCheckDate
        while (checkDate.isBefore(today) || (checkDate == today && !now.isBefore(depositTime))) {
            val salary = getDailySalary(checkDate.year, checkDate.monthValue)
            if (salary > 0) {
                val depositTimestamp = checkDate.atTime(depositTime)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()

                val txn = Transaction.create(
                    category = Category.AUTOMATIC,
                    amount = salary,
                    timestamp = depositTimestamp,
                    note = "日薪自动入账 ${checkDate}"
                )
                repository.add(txn)
                newDeposits.add(txn)
            }
            checkDate = checkDate.plusDays(1)
        }

        // 更新最后入账日期。
        // checkDate 是循环结束后第一个未处理的日期：
        // - 若今天已过 0:00，checkDate == 明天 → 最后处理的是今天
        // - 首次打开，checkDate == 今天 → 处理今天
        val lastSettled = checkDate.minusDays(1)
        if (!lastSettled.isBefore(startCheckDate)) {
            // 至少处理了一个日期
            lastDepositDate = lastSettled.toString()
        } else if (lastDepositDate == null) {
            // 首次打开未处理任何日期，标记昨天为已结算
            lastDepositDate = today.minusDays(1).toString()
        }

        return newDeposits
    }

    /**
     * 从持久化状态恢复服务参数。
     */
    fun restoreState(
        monthlyIncome: Double,
        dailyIncome: Double,
        useDailyMode: Boolean,
        lastDepositDate: String?
    ) {
        this.monthlyIncome = monthlyIncome
        this.dailyIncome = dailyIncome
        this.useDailyMode = useDailyMode
        this.lastDepositDate = lastDepositDate
    }
}
