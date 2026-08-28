package ai.findnextstep.budget.logic.service.codingplan

/**
 * Coding Plan 各平台 Provider 注册表。新增平台时在此追加。
 */
object CodingPlanProviders {
    val all: List<CodingPlanProvider> = listOf(
        KimiCodingPlanProvider(),
        GlmCodingPlanProvider(),
        DeepSeekBalanceProvider()
    )

    fun byId(id: String): CodingPlanProvider? = all.find { it.id == id }
}
