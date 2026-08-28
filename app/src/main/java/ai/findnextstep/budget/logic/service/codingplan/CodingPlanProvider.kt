package ai.findnextstep.budget.logic.service.codingplan

import ai.findnextstep.budget.logic.model.UsageSnapshot

/**
 * Coding Plan 用量查询提供者。每个平台（Kimi / GLM / DeepSeek）一个实现。
 */
interface CodingPlanProvider {
    /** 平台标识，如 "kimi" */
    val id: String

    /** 展示名称，如「Kimi Code」 */
    val displayName: String

    /**
     * 查询用量。
     * @throws CodingPlanException 已转换为用户可读信息的异常
     */
    suspend fun fetchUsage(apiKey: String): UsageSnapshot

    /** 解析响应（也用于从本地缓存恢复） */
    fun parse(json: String, fetchedAtMillis: Long): UsageSnapshot
}

class CodingPlanException(message: String) : Exception(message)
