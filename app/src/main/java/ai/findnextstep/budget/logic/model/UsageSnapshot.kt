package ai.findnextstep.budget.logic.model

/**
 * 单个限额窗口的用量。
 * @param label 展示名称，如「本周用量」「5小时限额」
 * @param used 已用量（tokens）
 * @param limit 总量（tokens）
 * @param resetAtMillis 重置时间点（绝对时间），未知为 null
 * @param percentOverride 平台直接给出的已用百分比（0~1），优先于 used/limit 计算
 */
data class UsageLimit(
    val label: String,
    val used: Long,
    val limit: Long,
    val resetAtMillis: Long? = null,
    val percentOverride: Float? = null
) {
    val percent: Float
        get() = percentOverride?.coerceIn(0f, 1f)
            ?: if (limit > 0) (used.toFloat() / limit).coerceIn(0f, 1f) else 0f
}

/**
 * 账户余额信息（按币种）。
 */
data class BalanceInfo(
    val currency: String,
    val total: String,
    val granted: String? = null,
    val toppedUp: String? = null
)

/**
 * 某平台 Coding Plan 的一次用量查询结果。
 */
data class UsageSnapshot(
    val provider: String,
    val summary: UsageLimit?,
    val limits: List<UsageLimit>,
    val fetchedAtMillis: Long,
    /** 余额列表（DeepSeek 等按余额计的平台使用） */
    val balances: List<BalanceInfo> = emptyList(),
    /** 原始响应 JSON，用于本地缓存 */
    val rawJson: String = ""
)
