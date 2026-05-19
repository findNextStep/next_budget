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
import ai.findnextstep.budget.logic.model.Transaction
import ai.findnextstep.budget.ui.component.CategorySelector
import ai.findnextstep.budget.ui.component.NumberPad
import ai.findnextstep.budget.ui.viewmodel.BudgetViewModel
import ai.findnextstep.budget.ui.viewmodel.Screen
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    viewModel: BudgetViewModel,
    isIncome: Boolean,
    initialAmount: String = "",
    editingTransaction: Transaction? = null
) {
    val isEditing = editingTransaction != null
    val existingTxn = editingTransaction

    var amount by remember {
        mutableStateOf(
            if (existingTxn != null) abs(existingTxn.amount).toBigDecimal().stripTrailingZeros().toPlainString()
            else initialAmount
        )
    }
    var selectedCategory by remember { mutableStateOf<Category?>(existingTxn?.category) }
    var note by remember { mutableStateOf(existingTxn?.note ?: "") }
    val categories = if (isIncome) Category.incomeCategories else Category.expenseCategories

    LaunchedEffect(Unit) {
        if (initialAmount.isNotEmpty()) {
            viewModel.updateFloatingAmount("")
        }
    }

    BackHandler(onBack = { viewModel.goBack() })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "编辑交易" else if (isIncome) "记收入" else "记支出") },
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
                        if (isEditing && existingTxn != null) {
                            viewModel.updateTransaction(
                                existingTxn.copy(
                                    amount = finalAmount,
                                    category = selectedCategory!!,
                                    note = note
                                )
                            )
                        } else {
                            viewModel.addTransaction(selectedCategory!!, finalAmount, note)
                            viewModel.goBack()
                        }
                    }
                },
                showDecimal = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            CategorySelector(
                categories = categories,
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
                        val finalAmount = if (isIncome) amt else -amt
                        if (isEditing && existingTxn != null) {
                            viewModel.updateTransaction(
                                existingTxn.copy(
                                    amount = finalAmount,
                                    category = selectedCategory!!,
                                    note = note
                                )
                            )
                        } else {
                            viewModel.addTransaction(selectedCategory!!, finalAmount, note)
                            viewModel.goBack()
                        }
                    }
                },
                enabled = amount.toDoubleOrNull() != null && (amount.toDoubleOrNull() ?: 0.0) > 0 && selectedCategory != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(48.dp)
            ) {
                Text(if (isEditing) "保存修改" else "确认记账")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
