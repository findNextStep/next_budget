package ai.findnextstep.budget.logic.model

/**
 * 单个限额窗口的用量。
 * @param label 展示名称，如「本周用量」「5小时限额」
 * @param used 已用量（tokens）
 * @param limit 总量（tokens）
 * @param resetAtMillis 重置时间点（绝对时间），未知为 null
 */
data class UsageLimit(
    val label: String,
    val used: Long,
    val limit: Long,
    val resetAtMillis: Long? = null
) {
    val percent: Float
        get() = if (limit > 0) (used.toFloat() / limit).coerceIn(0f, 1f) else 0f
}

/**
 * 某平台 Coding Plan 的一次用量查询结果。
 */
data class UsageSnapshot(
    val provider: String,
    val summary: UsageLimit?,
    val limits: List<UsageLimit>,
    val fetchedAtMillis: Long,
    /** 原始响应 JSON，用于本地缓存 */
    val rawJson: String = ""
)
