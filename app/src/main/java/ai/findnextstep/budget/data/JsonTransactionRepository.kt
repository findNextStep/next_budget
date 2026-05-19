package ai.findnextstep.budget.data

import ai.findnextstep.budget.logic.model.Category
import ai.findnextstep.budget.logic.model.Transaction
import ai.findnextstep.budget.logic.repository.TransactionRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 基于 JSON 文件的账目持久化实现。
 * 文件路径由外部通过 [load] 提供。
 */
class JsonTransactionRepository : TransactionRepository {

    private val transactions = mutableListOf<Transaction>()
    private var filePath: String? = null

    /**
     * 从指定文件加载数据。应在应用启动时调用。
     */
    fun load(path: String) {
        filePath = path
        val file = File(path)
        if (!file.exists()) {
            transactions.clear()
            return
        }
        try {
            val json = file.readText()
            val arr = JSONArray(json)
            transactions.clear()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val txn = Transaction(
                    id = obj.getString("id"),
                    timestamp = obj.getLong("timestamp"),
                    category = Category.fromKey(obj.getString("categoryKey")),
                    amount = obj.getDouble("amount"),
                    note = obj.optString("note", "")
                )
                transactions.add(txn)
            }
        } catch (_: Exception) {
            transactions.clear()
        }
    }

    /**
     * 持久化到文件。
     */
    @Synchronized
    private fun save() {
        val path = filePath ?: return
        val arr = JSONArray()
        for (txn in transactions) {
            val obj = JSONObject().apply {
                put("id", txn.id)
                put("timestamp", txn.timestamp)
                put("categoryKey", txn.category.key)
                put("amount", txn.amount)
                put("note", txn.note)
            }
            arr.put(obj)
        }
        try {
            File(path).writeText(arr.toString())
        } catch (_: Exception) {
            // 静默处理；下次保存重试
        }
    }

    override fun getAll(): List<Transaction> = transactions.toList()

    override fun getByTimeRange(startMillis: Long, endMillis: Long): List<Transaction> {
        return transactions.filter { it.timestamp in startMillis..endMillis }
    }

    override fun getById(id: String): Transaction? = transactions.find { it.id == id }

    @Synchronized
    override fun add(transaction: Transaction) {
        transactions.add(transaction)
        save()
    }

    @Synchronized
    override fun delete(id: String) {
        transactions.removeAll { it.id == id }
        save()
    }

    @Synchronized
    override fun update(transaction: Transaction) {
        val idx = transactions.indexOfFirst { it.id == transaction.id }
        if (idx >= 0) {
            transactions[idx] = transaction
            save()
        }
    }

    override fun count(): Int = transactions.size

    override fun getRecent(limit: Int): List<Transaction> {
        return transactions.sortedByDescending { it.timestamp }.take(limit)
    }

    /**
     * 批量导入（不走单条保存，统一刷盘）。
     */
    @Synchronized
    fun importAll(list: List<Transaction>) {
        transactions.addAll(list)
        save()
    }
}
