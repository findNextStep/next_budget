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
import ai.findnextstep.budget.ui.viewmodel.BudgetViewModel
import ai.findnextstep.budget.ui.viewmodel.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    viewModel: BudgetViewModel,
    isIncome: Boolean,
    initialAmount: String = ""
) {
    var amount by remember { mutableStateOf(initialAmount) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var note by remember { mutableStateOf("") }
    val categories = if (isIncome) Category.incomeCategories else Category.expenseCategories

    // 消费掉预填金额后清除 ViewModel 中的暂存，避免下次正常打开时误带
    LaunchedEffect(Unit) {
        if (initialAmount.isNotEmpty()) {
            viewModel.updateFloatingAmount("")
        }
    }

    BackHandler(onBack = { viewModel.goBack() })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isIncome) "记收入" else "记支出") },
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
            // 数字输入
            NumberPad(
                value = amount,
                onValueChange = { newVal ->
                    amount = newVal
                    // 自动推断类型
                    val amt = newVal.toDoubleOrNull()
                    if (amt != null && amt > 0) {
                        val predicted = viewModel.categoryPredictor.predict(
                            if (isIncome) amt else -amt
                        )
                        if (predicted != null) selectedCategory = predicted
                    }
                },
                onConfirm = {
                    val amt = amount.toDoubleOrNull()
                    if (amt != null && amt > 0 && selectedCategory != null) {
                        val finalAmount = if (isIncome) amt else -amt
                        viewModel.addTransaction(selectedCategory!!, finalAmount, note)
                        viewModel.goBack()
                    }
                },
                showDecimal = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 类型选择
            CategorySelector(
                categories = categories,
                selectedCategory = selectedCategory,
                onCategorySelected = { selectedCategory = it }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 备注
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

            // 确认按钮
            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull()
                    if (amt != null && amt > 0 && selectedCategory != null) {
                        val finalAmount = if (isIncome) amt else -amt
                        viewModel.addTransaction(selectedCategory!!, finalAmount, note)
                        viewModel.goBack()
                    }
                },
                enabled = amount.toDoubleOrNull() != null && (amount.toDoubleOrNull() ?: 0.0) > 0 && selectedCategory != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(48.dp)
            ) {
                Text("确认记账")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
