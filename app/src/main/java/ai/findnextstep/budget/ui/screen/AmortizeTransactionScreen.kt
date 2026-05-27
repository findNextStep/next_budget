package ai.findnextstep.budget.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.findnextstep.budget.logic.model.Category
import ai.findnextstep.budget.ui.component.CategorySelector
import ai.findnextstep.budget.ui.component.NumberPad
import ai.findnextstep.budget.ui.theme.ExpenseRed
import ai.findnextstep.budget.ui.viewmodel.BudgetViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmortizeTransactionScreen(
    viewModel: BudgetViewModel
) {
    var amount by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var note by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("MONTH") }

    val totalAmt = amount.toDoubleOrNull() ?: 0.0
    val days = durationDays(duration)
    val dailyAmt = if (days > 0) totalAmt / days else 0.0

    BackHandler(onBack = { viewModel.goBack() })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("长期支出") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.goBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NumberPad(
                value = amount,
                onValueChange = { newVal ->
                    amount = newVal
                    val amt = newVal.toDoubleOrNull()
                    if (amt != null && amt > 0) {
                        val predicted = viewModel.categoryPredictor.predict(-amt)
                        if (predicted != null) selectedCategory = predicted
                    }
                },
                onConfirm = {
                    val amt = amount.toDoubleOrNull()
                    if (amt != null && amt > 0 && selectedCategory != null) {
                        viewModel.addAmortizedExpense(amt, selectedCategory!!, duration, note)
                    }
                },
                showDecimal = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "时长",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))

            val durations = listOf(
                "WEEK" to "周",
                "MONTH" to "月",
                "TWO_MONTHS" to "两月",
                "YEAR" to "年"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                durations.forEach { (key, label) ->
                    FilterChip(
                        selected = duration == key,
                        onClick = { duration = key },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (totalAmt > 0 && days > 0) {
                Text(
                    "¥${"%.2f".format(totalAmt)} ÷ ${days}天 = ¥${"%.2f".format(dailyAmt)}/天",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ExpenseRed,
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            CategorySelector(
                categories = Category.expenseCategories,
                selectedCategory = selectedCategory,
                onCategorySelected = { selectedCategory = it }
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("备注（可选）") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull()
                    if (amt != null && amt > 0 && selectedCategory != null) {
                        viewModel.addAmortizedExpense(amt, selectedCategory!!, duration, note)
                    }
                },
                enabled = amount.toDoubleOrNull() != null && (amount.toDoubleOrNull() ?: 0.0) > 0 && selectedCategory != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(48.dp)
            ) {
                Text("确认分摊")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private fun durationDays(duration: String): Int {
    val today = java.time.LocalDate.now()
    return when (duration) {
        "WEEK" -> 7
        "MONTH" -> today.plusMonths(1).lengthOfMonth()
        "TWO_MONTHS" -> {
            val m1 = today.plusMonths(1)
            val m2 = today.plusMonths(2)
            m1.lengthOfMonth() + m2.lengthOfMonth()
        }
        "YEAR" -> java.time.Year.of(today.year + 1).length()
        else -> 0
    }
}
