package ai.findnextstep.budget.logic.service.codingplan

import ai.findnextstep.budget.logic.model.UsageSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * DeepSeek 余额查询（官方接口 GET https://api.deepseek.com/user/balance）。
 * 解析逻辑见 [DeepSeekBalanceParser]。
 */
class DeepSeekBalanceProvider(
    private val endpoint: String = DEFAULT_ENDPOINT
) : CodingPlanProvider {

    override val id: String = "deepseek"
    override val displayName: String = "DeepSeek"

    override suspend fun fetchUsage(apiKey: String): UsageSnapshot = withContext(Dispatchers.IO) {
        val (code, body) = CodingPlanHttp.get(
            endpoint,
            mapOf("Authorization" to "Bearer $apiKey")
        )
        when (code) {
            200 -> DeepSeekBalanceParser.parse(body, System.currentTimeMillis())
            401 -> throw CodingPlanException("API Key 认证失败（401），请确认使用 DeepSeek 开放平台的 API Key")
            403 -> throw CodingPlanException("API 拒绝访问（403），请检查 Key 权限")
            429 -> throw CodingPlanException("请求过于频繁（429），请稍后重试")
            else -> throw CodingPlanException("查询失败（HTTP $code）")
        }
    }

    override fun parse(json: String, fetchedAtMillis: Long): UsageSnapshot =
        DeepSeekBalanceParser.parse(json, fetchedAtMillis)

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.deepseek.com/user/balance"
    }
}
