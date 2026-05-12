package ai.findnextstep.budget.logic.service

import ai.findnextstep.budget.logic.model.Category
import ai.findnextstep.budget.logic.model.Transaction
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * CSV 导入导出服务。
 * CSV 格式：date,time,amount,original category,category
 * 参考 example.csv。
 */
class CsvService {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * 从 InputStream 导入 CSV，返回解析出的交易列表。
     */
    fun importFromStream(inputStream: InputStream): List<Transaction> {
        val transactions = mutableListOf<Transaction>()
        val reader = BufferedReader(InputStreamReader(inputStream))
        var isFirstLine = true

        reader.useLines { lines ->
            lines.forEach { line ->
                if (isFirstLine) {
                    isFirstLine = false
                    return@forEach // 跳过表头
                }
                val txn = parseLine(line)
                if (txn != null) {
                    transactions.add(txn)
                }
            }
        }
        return transactions
    }

    private fun parseLine(line: String): Transaction? {
        return try {
            val parts = line.split(",", limit = 5)
            if (parts.size < 5) return null

            val dateStr = parts[0].trim()
            val timeStr = parts[1].trim()
            val amountStr = parts[2].trim()
            val originalCategory = parts[3].trim()
            val categoryStr = parts[4].trim()

            val date = LocalDate.parse(dateStr, dateFormatter)
            val time = LocalTime.parse(timeStr, timeFormatter)
            val timestamp = date.atTime(time)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            val amount = amountStr.toDouble()

            val category = Category.fromKey(categoryStr)

            Transaction.create(
                category = category,
                amount = amount,
                timestamp = timestamp,
                note = originalCategory
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 将交易列表导出为 CSV 字符串。
     */
    fun exportToString(transactions: List<Transaction>): String {
        val sb = StringBuilder()
        sb.appendLine("date,time,amount,original category,category")
        val sorted = transactions.sortedBy { it.timestamp }

        sorted.forEach { txn ->
            val ldt = txn.toLocalDateTime()
            val dateStr = ldt.toLocalDate().format(dateFormatter)
            val timeStr = ldt.toLocalTime().format(timeFormatter)
            val amountStr = formatAmount(txn.amount)
            val categoryStr = txn.category.key
            val note = txn.note.ifEmpty { categoryStr }

            sb.appendLine("$dateStr,$timeStr,$amountStr,$note,$categoryStr")
        }
        return sb.toString()
    }

    /**
     * 金额格式化：去除末尾多余的零。
     */
    private fun formatAmount(amount: Double): String {
        return if (amount == amount.toLong().toDouble()) {
            amount.toLong().toString()
        } else {
            String.format("%.2f", amount)
        }
    }
}
