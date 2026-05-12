package ai.findnextstep.budget.logic.service

import ai.findnextstep.budget.logic.model.Category
import ai.findnextstep.budget.logic.repository.TransactionRepository

/**
 * 类型预测器。
 * 根据给定的金额，在历史记录中查找最近发生的同金额交易，返回其类型。
 * 用于快速记账时自动推断类型。
 */
class CategoryPredictor(private val repository: TransactionRepository) {

    /**
     * 预测类型。
     * @param amount 交易金额（正数=收入，负数=支出）
     * @return 预测的类型，若无匹配历史则返回 null
     */
    fun predict(amount: Double): Category? {
        val allTransactions = repository.getAll()
        // 按时间降序排列，找到第一个相同金额的交易
        val matched = allTransactions
            .filter { it.amount == amount }
            .maxByOrNull { it.timestamp }
        return matched?.category
    }

    /**
     * 预测类型，带默认值。
     */
    fun predictOrDefault(amount: Double, default: Category = Category.OTHER): Category {
        return predict(amount) ?: default
    }
}
