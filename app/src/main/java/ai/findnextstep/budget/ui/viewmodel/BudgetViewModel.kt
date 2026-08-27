package ai.findnextstep.budget.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.findnextstep.budget.data.JsonTransactionRepository
import ai.findnextstep.budget.logic.model.*
import ai.findnextstep.budget.logic.service.*
import ai.findnextstep.budget.logic.service.codingplan.CodingPlanException
import ai.findnextstep.budget.logic.service.codingplan.KimiCodingPlanProvider
import ai.findnextstep.budget.logic.service.codingplan.KimiUsageParser
import ai.findnextstep.budget.ui.service.FloatingExpenseService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 应用整体 UI 状态。
 */
data class BudgetUiState(
    val transactions: List<Transaction> = emptyList(),
    val recentTransactions: List<Transaction> = emptyList(),
    val currentPeriod: Period = Period.MONTH,
    val referenceDate: LocalDate = LocalDate.now(),
    val periodStatistics: PeriodStatistics? = null,
    val slideDirection: Int = 0,
    val monthlyIncome: Double = 0.0,
    val dailyIncome: Double = 0.0,
    val useDailyMode: Boolean = false,
    val todayDailySalary: Double = 0.0,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val currentScreen: Screen = Screen.MAIN,
    val floatingWindowEnabled: Boolean = false,
    val hideHint: Boolean = false,
    val showFloatingInput: Boolean = false,
    val floatingAmount: String = "",
    val floatingCategory: Category? = null,
    val isLoading: Boolean = false,
    val editingTransaction: Transaction? = null,
    val dayDetailDate: String? = null,
    // ── Coding Plan 用量 ──
    val kimiApiKey: String = "",
    val kimiUsage: UsageSnapshot? = null,
    val kimiUsageLoading: Boolean = false,
    val kimiUsageError: String? = null,
    val codingPlanGuideDismissed: Boolean = false
)

enum class ThemeMode(val label: String) {
    LIGHT("白色"),
    DARK("黑色"),
    BLACK("纯黑"),
    SYSTEM("跟随系统");
}

enum class Screen {
    MAIN,
    ADD_EXPENSE,
    ADD_INCOME,
    EDIT_TRANSACTION,
    AMORTIZE_EXPENSE,
    STATISTICS,
    SETTINGS
}

class BudgetViewModel(application: Application) : AndroidViewModel(application) {

    // ── 仓储 ──
    private val repository = JsonTransactionRepository()

    // ── 服务 ──
    val statisticsService = StatisticsService(repository)
    val salaryService = SalaryService(repository)
    val categoryPredictor = CategoryPredictor(repository)
    val csvService = CsvService()
    private val kimiProvider = KimiCodingPlanProvider()

    // ── UI 状态 ──
    private val _uiState = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    // ── 持久化键 ──
    private val prefs = application.getSharedPreferences("budget_prefs", 0)

    // 监听悬浮窗开关的 SharedPreferences 变化（外部关闭时联动 UI）
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == FloatingExpenseService.PREF_FLOATING_ENABLED) {
            _uiState.update {
                it.copy(floatingWindowEnabled = prefs.getBoolean(key, false))
            }
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        // 加载数据
        val dataPath = application.filesDir.resolve("transactions.json").absolutePath
        repository.load(dataPath)

        // 恢复薪资状态
        val monthly = prefs.getFloat("monthly_income", 0f).toDouble()
        val daily = prefs.getFloat("daily_income", 0f).toDouble()
        val useDaily = prefs.getBoolean("use_daily_mode", false)
        val lastDeposit = prefs.getString("last_deposit_date", null)
        salaryService.restoreState(monthly, daily, useDaily, lastDeposit)

        // 恢复主题
        val themeStr = prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        val themeMode = try { ThemeMode.valueOf(themeStr) } catch (_: Exception) { ThemeMode.SYSTEM }
        val floatingEnabled = FloatingExpenseService.isEnabled(getApplication())
        val hideHint = prefs.getBoolean(FloatingExpenseService.PREF_HIDE_HINT, false)
        if (floatingEnabled) {
            getApplication<Application>().startForegroundService(
                Intent(getApplication(), FloatingExpenseService::class.java)
            )
        }

        _uiState.update {
            it.copy(
                monthlyIncome = monthly,
                dailyIncome = daily,
                useDailyMode = useDaily,
                todayDailySalary = salaryService.getTodayDailySalary(),
                themeMode = themeMode,
                floatingWindowEnabled = floatingEnabled,
                hideHint = hideHint,
                kimiApiKey = prefs.getString(PREF_KIMI_API_KEY, "") ?: "",
                kimiUsage = loadCachedKimiUsage(),
                codingPlanGuideDismissed = prefs.getBoolean(PREF_CODING_PLAN_GUIDE_DISMISSED, false)
            )
        }

        // 处理待入账
        processPendingDeposits()

        // 刷新列表
        refreshTransactions()
    }

    /** 从磁盘重新加载数据（悬浮窗可能已写入新记录） */
    fun reloadFromDisk() {
        val dataPath = getApplication<Application>().filesDir.resolve("transactions.json").absolutePath
        repository.load(dataPath)
        refreshTransactions()
    }

    // ────────────────────── 公开操作 ──────────────────────

    /** 添加一笔交易 */
    fun addTransaction(category: Category, amount: Double, note: String = "") {
        val txn = Transaction.create(category = category, amount = amount, note = note)
        repository.add(txn)
        refreshTransactions()
    }

    /** 删除一笔交易 */
    fun deleteTransaction(id: String) {
        repository.delete(id)
        refreshTransactions()
    }

    /** 导航到指定界面 */
    fun navigateTo(screen: Screen) {
        _uiState.update { it.copy(currentScreen = screen, floatingAmount = "") }
    }

    /** 从悬浮窗「其他」跳转打开记支出页面，预填金额 */
    fun openAddExpenseWithAmount(amount: String) {
        _uiState.update {
            it.copy(
                currentScreen = Screen.ADD_EXPENSE,
                floatingAmount = amount
            )
        }
    }

    /** 返回主界面 */
    fun goBack() {
        _uiState.update { it.copy(currentScreen = Screen.MAIN, editingTransaction = null, dayDetailDate = null) }
    }

    fun setPeriod(period: Period) {
        _uiState.update { it.copy(currentPeriod = period, referenceDate = LocalDate.now(), slideDirection = 0, dayDetailDate = null) }
        refreshStatistics()
    }

    /** 跳转到编辑交易页面 */
    fun navigateToEditTransaction(transaction: Transaction) {
        _uiState.update {
            it.copy(
                currentScreen = Screen.EDIT_TRANSACTION,
                editingTransaction = transaction
            )
        }
    }

    /** 更新交易 */
    fun updateTransaction(transaction: Transaction) {
        repository.update(transaction)
        refreshTransactions()
        goBack()
    }

    /** 打开日详情（从周/月/年视图点击某天） */
    fun openDayDetail(date: String) {
        _uiState.update { it.copy(dayDetailDate = date) }
    }

    /** 关闭日详情，返回周期视图 */
    fun closeDayDetail() {
        _uiState.update { it.copy(dayDetailDate = null) }
    }

    /** 长期分摊：在下一完整周期内每日生成一笔支出 */
    fun addAmortizedExpense(
        totalAmount: Double,
        category: Category,
        duration: String,
        note: String
    ) {
        val today = LocalDate.now()
        val zone = java.time.ZoneId.systemDefault()

        val (startDate, days) = when (duration) {
            "WEEK" -> {
                val nextMon = today.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY))
                nextMon to 7
            }
            "MONTH" -> {
                today.plusMonths(1).withDayOfMonth(1) to today.plusMonths(1).lengthOfMonth()
            }
            "TWO_MONTHS" -> {
                val first = today.plusMonths(1).withDayOfMonth(1)
                val last = today.plusMonths(2)
                first to java.time.Period.between(first, last.withDayOfMonth(1)).days + last.lengthOfMonth()
            }
            "YEAR" -> {
                java.time.LocalDate.of(today.year + 1, 1, 1) to java.time.Year.of(today.year + 1).length()
            }
            else -> return
        }

        val dailyAmount = totalAmount / days
        val baseTs = startDate.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

        for (d in 0 until days) {
            repository.add(
                Transaction.create(
                    category = category,
                    amount = -dailyAmount,
                    timestamp = baseTs + d * 24 * 60 * 60 * 1000L,
                    note = "$note (${d + 1}/$days)"
                )
            )
        }

        refreshTransactions()
        goBack()
    }

    /** 翻到上一个周期 */
    fun goToPreviousPeriod() {
        val state = _uiState.value
        val prevDate = when (state.currentPeriod) {
            Period.DAY -> state.referenceDate.minusDays(1)
            Period.WEEK -> state.referenceDate.minusWeeks(1)
            Period.MONTH -> state.referenceDate.minusMonths(1)
            Period.YEAR -> state.referenceDate.minusYears(1)
        }
        _uiState.update { it.copy(referenceDate = prevDate, slideDirection = -1) }
        refreshStatistics()
    }

    /** 翻到下一个周期（不超过今天） */
    fun goToNextPeriod() {
        val state = _uiState.value
        val today = LocalDate.now()
        val nextDate = when (state.currentPeriod) {
            Period.DAY -> state.referenceDate.plusDays(1)
            Period.WEEK -> state.referenceDate.plusWeeks(1)
            Period.MONTH -> state.referenceDate.plusMonths(1)
            Period.YEAR -> state.referenceDate.plusYears(1)
        }
        if (nextDate.isAfter(today)) return
        _uiState.update { it.copy(referenceDate = nextDate, slideDirection = 1) }
        refreshStatistics()
    }

    /** 重置滑动方向（动画结束后调用） */
    fun resetSlideDirection() {
        _uiState.update { it.copy(slideDirection = 0) }
    }

    /** 设置月收入 */
    fun setMonthlyIncome(income: Double) {
        salaryService.setMonthlyIncome(income)
        prefs.edit().putFloat("monthly_income", income.toFloat()).apply()
        prefs.edit().putBoolean("use_daily_mode", false).apply()
        _uiState.update {
            it.copy(
                monthlyIncome = income,
                useDailyMode = false,
                todayDailySalary = salaryService.getTodayDailySalary()
            )
        }
    }

    /** 设置日收入 */
    fun setDailyIncome(income: Double) {
        salaryService.setDailyIncome(income)
        prefs.edit().putFloat("daily_income", income.toFloat()).apply()
        prefs.edit().putBoolean("use_daily_mode", true).apply()
        _uiState.update {
            it.copy(
                dailyIncome = income,
                useDailyMode = true,
                todayDailySalary = salaryService.getTodayDailySalary()
            )
        }
    }

    /** 设置主题模式 */
    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _uiState.update { it.copy(themeMode = mode) }
    }

    /** 设置是否隐藏提示文本 */
    fun setHideHint(hide: Boolean) {
        prefs.edit().putBoolean(FloatingExpenseService.PREF_HIDE_HINT, hide).apply()
        FloatingExpenseService.hideHintState.value = hide
        _uiState.update { it.copy(hideHint = hide) }
    }

    // ── Coding Plan 用量 ──

    /** 保存 Kimi Code API Key */
    fun setKimiApiKey(key: String) {
        val trimmed = key.trim()
        prefs.edit().putString(PREF_KIMI_API_KEY, trimmed).apply()
        _uiState.update { it.copy(kimiApiKey = trimmed, kimiUsageError = null) }
    }

    /** 关闭主页 Coding Plan 配置引导卡片 */
    fun dismissCodingPlanGuide() {
        prefs.edit().putBoolean(PREF_CODING_PLAN_GUIDE_DISMISSED, true).apply()
        _uiState.update { it.copy(codingPlanGuideDismissed = true) }
    }

    /** 手动刷新 Kimi Coding Plan 用量 */
    fun refreshKimiUsage() {
        val key = _uiState.value.kimiApiKey
        if (key.isEmpty() || _uiState.value.kimiUsageLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(kimiUsageLoading = true, kimiUsageError = null) }
            try {
                val snapshot = kimiProvider.fetchUsage(key)
                cacheKimiUsage(snapshot)
                _uiState.update { it.copy(kimiUsage = snapshot, kimiUsageLoading = false) }
            } catch (e: CodingPlanException) {
                _uiState.update { it.copy(kimiUsageLoading = false, kimiUsageError = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(kimiUsageLoading = false, kimiUsageError = "查询失败：${e.message}") }
            }
        }
    }

    /** 切换悬浮窗开关 */
    fun toggleFloatingWindow(enabled: Boolean) {
        val app = getApplication<Application>()
        if (enabled) {
            FloatingExpenseService.start(app)
        } else {
            FloatingExpenseService.stop(app)
        }
        _uiState.update {
            it.copy(floatingWindowEnabled = FloatingExpenseService.isEnabled(app))
        }
    }

    /** 处理待入账的日薪 */
    fun processPendingDeposits() {
        val newDeposits = salaryService.processPendingDeposits()
        prefs.edit().putString("last_deposit_date", salaryService.lastDepositDate).apply()
        if (newDeposits.isNotEmpty()) {
            refreshTransactions()
        }
    }

    /** 浮动窗口：更新输入金额 */
    fun updateFloatingAmount(value: String) {
        _uiState.update { it.copy(floatingAmount = value) }
    }

    /** 浮动窗口：显示/隐藏 */
    fun toggleFloatingInput(show: Boolean) {
        if (show) {
            _uiState.update { it.copy(showFloatingInput = true, floatingAmount = "", floatingCategory = null) }
        } else {
            _uiState.update { it.copy(showFloatingInput = false) }
        }
    }

    /** 浮动窗口：提交快速支出 */
    fun submitFloatingExpense(amount: Double) {
        val category = _uiState.value.floatingCategory ?: Category.OTHER
        addTransaction(category, -amount)
        _uiState.update { it.copy(showFloatingInput = false, floatingAmount = "", floatingCategory = null) }
    }

    /** 浮动窗口：自动推断类型 */
    fun predictFloatingCategory(amount: Double) {
        val predicted = categoryPredictor.predict(-amount)
        _uiState.update { it.copy(floatingCategory = predicted) }
    }

    /** CSV 导入 */
    fun importCsv(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val imported = csvService.importFromStream(inputStream)
                    repository.importAll(imported)
                    refreshTransactions()
                }
            } catch (_: Exception) {
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /** CSV 导出（返回内容字符串，由 UI 写入文件） */
    fun exportCsv(): String {
        return csvService.exportToString(repository.getAll())
    }

    /** 手动设置浮动分类 */
    fun setFloatingCategory(category: Category) {
        _uiState.update { it.copy(floatingCategory = category) }
    }

    // ────────────────────── 内部方法 ──────────────────────

    private fun refreshTransactions() {
        val all = repository.getAll().sortedByDescending { it.timestamp }
        _uiState.update {
            it.copy(
                transactions = all,
                recentTransactions = all.take(20)
            )
        }
        refreshStatistics()
    }

    private fun refreshStatistics() {
        val state = _uiState.value
        val stats = statisticsService.getStatistics(state.referenceDate, state.currentPeriod)
        _uiState.update { it.copy(periodStatistics = stats) }
    }

    // ── Coding Plan 用量缓存（保存原始响应 + 查询时间） ──

    private fun cacheKimiUsage(snapshot: UsageSnapshot) {
        prefs.edit()
            .putString(PREF_KIMI_USAGE_CACHE, snapshot.rawJson)
            .putLong(PREF_KIMI_USAGE_FETCHED_AT, snapshot.fetchedAtMillis)
            .apply()
    }

    private fun loadCachedKimiUsage(): UsageSnapshot? {
        val raw = prefs.getString(PREF_KIMI_USAGE_CACHE, null) ?: return null
        val fetchedAt = prefs.getLong(PREF_KIMI_USAGE_FETCHED_AT, 0L)
        return try {
            KimiUsageParser.parse(raw, fetchedAt)
        } catch (_: Exception) {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    companion object {
        const val PREF_KIMI_API_KEY = "kimi_api_key"
        const val PREF_KIMI_USAGE_CACHE = "kimi_usage_cache"
        const val PREF_KIMI_USAGE_FETCHED_AT = "kimi_usage_fetched_at"
        const val PREF_CODING_PLAN_GUIDE_DISMISSED = "coding_plan_guide_dismissed"
    }
}
