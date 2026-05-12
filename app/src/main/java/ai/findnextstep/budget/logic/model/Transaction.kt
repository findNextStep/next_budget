package ai.findnextstep.budget.logic.model

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * 一笔账目记录。
 * [amount] > 0 为收入，< 0 为支出。
 * [timestamp] 使用 epoch millis。
 */
data class Transaction(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val category: Category,
    val amount: Double,
    val note: String = ""
) {
    val isIncome: Boolean get() = amount > 0
    val isExpense: Boolean get() = amount < 0

    /** 转为 LocalDateTime（使用系统默认时区） */
    fun toLocalDateTime(): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())

    companion object {
        /**
         * 创建交易记录。
         * @param amount 正数=收入，负数=支出
         */
        fun create(
            category: Category,
            amount: Double,
            timestamp: Long = System.currentTimeMillis(),
            note: String = "",
            id: String = UUID.randomUUID().toString()
        ): Transaction = Transaction(
            id = id,
            timestamp = timestamp,
            category = category,
            amount = amount,
            note = note
        )
    }
}
