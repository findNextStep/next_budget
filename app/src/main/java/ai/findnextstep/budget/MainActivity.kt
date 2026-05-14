package ai.findnextstep.budget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import ai.findnextstep.budget.ui.screen.*
import ai.findnextstep.budget.ui.theme.BudgetTheme
import ai.findnextstep.budget.ui.viewmodel.BudgetViewModel
import ai.findnextstep.budget.ui.viewmodel.Screen

class MainActivity : ComponentActivity() {

    private val viewModel: BudgetViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        viewModel.reloadFromDisk()
        viewModel.processPendingDeposits()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val amount = intent?.getStringExtra("initial_amount") ?: return
        viewModel.openAddExpenseWithAmount(amount)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDeepLink(intent)
        setContent {
            val uiState by viewModel.uiState.collectAsState()

            BudgetTheme(themeMode = uiState.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (uiState.currentScreen) {
                        Screen.MAIN -> MainScreen(viewModel = viewModel, uiState = uiState)
                        Screen.ADD_EXPENSE -> AddTransactionScreen(viewModel = viewModel, isIncome = false, initialAmount = uiState.floatingAmount)
                        Screen.ADD_INCOME -> AddTransactionScreen(viewModel = viewModel, isIncome = true)
                        Screen.STATISTICS -> StatisticsScreen(viewModel = viewModel, uiState = uiState)
                        Screen.SETTINGS -> SettingsScreen(viewModel = viewModel, uiState = uiState)
                    }
                }
            }
        }
    }
}
