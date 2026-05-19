package ai.findnextstep.budget.logic.repository

import ai.findnextstep.budget.logic.model.Transaction

/**
 * 账目持久化仓储接口。逻辑层只依赖此接口，具体实现在数据层。
 */
interface TransactionRepository {
    /** 获取所有账目 */
    fun getAll(): List<Transaction>

    /** 按时间区间获取账目 */
    fun getByTimeRange(startMillis: Long, endMillis: Long): List<Transaction>

    /** 根据ID获取 */
    fun getById(id: String): Transaction?

    /** 添加一条账目 */
    fun add(transaction: Transaction)

    /** 删除一条账目 */
    fun delete(id: String)

    /** 更新一条账目 */
    fun update(transaction: Transaction)

    /** 获取总条数 */
    fun count(): Int

    /** 获取最近N条账目 */
    fun getRecent(limit: Int): List<Transaction>
}
