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
    val periodStatistics: PeriodStatistics? = null,
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
    val isLoading: Boolean = false
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
                hideHint = hideHint
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
        _uiState.update { it.copy(currentScreen = Screen.MAIN) }
    }

    /** 设置统计周期 */
    fun setPeriod(period: Period) {
        _uiState.update { it.copy(currentPeriod = period) }
        refreshStatistics()
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
        // 始终持久化 lastDepositDate，因为首次打开未到 12:00 时也会更新它
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
        val today = LocalDate.now()
        val stats = statisticsService.getStatistics(today, _uiState.value.currentPeriod)
        _uiState.update { it.copy(periodStatistics = stats) }
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }
}
