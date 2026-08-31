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
import ai.findnextstep.budget.logic.service.codingplan.CodingPlanProviders
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
    /** 各平台 API Key，key 为 provider id（kimi/glm/deepseek） */
    val codingPlanApiKeys: Map<String, String> = emptyMap(),
    /** 各平台用量状态 */
    val codingPlanStates: Map<String, ProviderUsageState> = emptyMap(),
    val codingPlanGuideDismissed: Boolean = false,
    /** 余额进度条上限（元），达到该值视为充足 */
    val balanceProgressMax: Double = BudgetViewModel.DEFAULT_BALANCE_PROGRESS_MAX
)

/** 单个平台的用量查询状态 */
data class ProviderUsageState(
    val snapshot: UsageSnapshot? = null,
    val loading: Boolean = false,
    val error: String? = null
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
                codingPlanApiKeys = loadCodingPlanApiKeys(),
                codingPlanStates = loadCachedCodingPlanStates(),
                codingPlanGuideDismissed = prefs.getBoolean(PREF_CODING_PLAN_GUIDE_DISMISSED, false),
                balanceProgressMax = prefs.getFloat(PREF_BALANCE_PROGRESS_MAX, DEFAULT_BALANCE_PROGRESS_MAX.toFloat()).toDouble()
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

    /** 保存某平台的 API Key */
    fun setCodingPlanApiKey(providerId: String, key: String) {
        val trimmed = key.trim()
        prefs.edit().putString(prefApiKey(providerId), trimmed).apply()
        _uiState.update {
            it.copy(codingPlanApiKeys = it.codingPlanApiKeys + (providerId to trimmed))
        }
        clearProviderError(providerId)
    }

    /** 关闭主页 Coding Plan 配置引导卡片 */
    fun dismissCodingPlanGuide() {
        prefs.edit().putBoolean(PREF_CODING_PLAN_GUIDE_DISMISSED, true).apply()
        _uiState.update { it.copy(codingPlanGuideDismissed = true) }
    }

    /** 设置余额进度条上限（元） */
    fun setBalanceProgressMax(max: Double) {
        if (max <= 0) return
        prefs.edit().putFloat(PREF_BALANCE_PROGRESS_MAX, max.toFloat()).apply()
        _uiState.update { it.copy(balanceProgressMax = max) }
    }

    /** 手动刷新某平台用量 */
    fun refreshProviderUsage(providerId: String) {
        val provider = CodingPlanProviders.byId(providerId) ?: return
        val key = _uiState.value.codingPlanApiKeys[providerId].orEmpty()
        if (key.isEmpty() || _uiState.value.codingPlanStates[providerId]?.loading == true) return
        viewModelScope.launch {
            updateProviderState(providerId) { it.copy(loading = true, error = null) }
            try {
                val snapshot = provider.fetchUsage(key)
                cacheProviderUsage(providerId, snapshot)
                updateProviderState(providerId) { it.copy(snapshot = snapshot, loading = false) }
            } catch (e: CodingPlanException) {
                updateProviderState(providerId) { it.copy(loading = false, error = e.message) }
            } catch (e: Exception) {
                updateProviderState(providerId) { it.copy(loading = false, error = "查询失败：${e.message}") }
            }
        }
    }

    /** 打开主页时调用：距上次刷新超过 24 小时（或从未刷新）的平台自动刷新一次 */
    fun autoRefreshStaleProviders() {
        val now = System.currentTimeMillis()
        CodingPlanProviders.all.forEach { provider ->
            val key = _uiState.value.codingPlanApiKeys[provider.id].orEmpty()
            if (key.isEmpty()) return@forEach
            val fetchedAt = _uiState.value.codingPlanStates[provider.id]?.snapshot?.fetchedAtMillis ?: 0L
            if (now - fetchedAt > PROVIDER_AUTO_REFRESH_INTERVAL_MILLIS) {
                refreshProviderUsage(provider.id)
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

    private fun updateProviderState(providerId: String, transform: (ProviderUsageState) -> ProviderUsageState) {
        _uiState.update { state ->
            val current = state.codingPlanStates[providerId] ?: ProviderUsageState()
            state.copy(codingPlanStates = state.codingPlanStates + (providerId to transform(current)))
        }
    }

    private fun clearProviderError(providerId: String) {
        updateProviderState(providerId) { it.copy(error = null) }
    }

    private fun loadCodingPlanApiKeys(): Map<String, String> =
        CodingPlanProviders.all.mapNotNull { p ->
            prefs.getString(prefApiKey(p.id), null)?.takeIf { it.isNotEmpty() }?.let { p.id to it }
        }.toMap()

    private fun loadCachedCodingPlanStates(): Map<String, ProviderUsageState> =
        CodingPlanProviders.all.mapNotNull { p ->
            val raw = prefs.getString(prefUsageCache(p.id), null) ?: return@mapNotNull null
            val fetchedAt = prefs.getLong(prefUsageFetchedAt(p.id), 0L)
            try {
                p.id to ProviderUsageState(snapshot = p.parse(raw, fetchedAt))
            } catch (_: Exception) {
                null
            }
        }.toMap()

    private fun cacheProviderUsage(providerId: String, snapshot: UsageSnapshot) {
        prefs.edit()
            .putString(prefUsageCache(providerId), snapshot.rawJson)
            .putLong(prefUsageFetchedAt(providerId), snapshot.fetchedAtMillis)
            .apply()
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    companion object {
        const val PREF_CODING_PLAN_GUIDE_DISMISSED = "coding_plan_guide_dismissed"
        const val PREF_BALANCE_PROGRESS_MAX = "balance_progress_max"

        /** 余额进度条默认上限（元） */
        const val DEFAULT_BALANCE_PROGRESS_MAX = 30.0

        /** Coding Plan 自动刷新间隔（24 小时） */
        const val PROVIDER_AUTO_REFRESH_INTERVAL_MILLIS = 24 * 60 * 60 * 1000L

        fun prefApiKey(providerId: String) = "${providerId}_api_key"
        fun prefUsageCache(providerId: String) = "${providerId}_usage_cache"
        fun prefUsageFetchedAt(providerId: String) = "${providerId}_usage_fetched_at"
    }
}
