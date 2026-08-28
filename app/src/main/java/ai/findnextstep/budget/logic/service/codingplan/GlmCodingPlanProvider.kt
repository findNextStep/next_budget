package ai.findnextstep.budget.logic.service.codingplan

import ai.findnextstep.budget.logic.model.UsageSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * GLM Coding Plan（智谱 bigmodel.cn）用量查询。
 *
 * 接口为未公开接口（GET /api/monitor/usage/quota/limit），响应结构可能变动，
 * 解析逻辑见 [GlmUsageParser]。
 * 注意：Authorization 头直接使用 API Key，不带 Bearer 前缀。
 */
class GlmCodingPlanProvider(
    private val endpoint: String = DEFAULT_ENDPOINT
) : CodingPlanProvider {

    override val id: String = "glm"
    override val displayName: String = "GLM Coding Plan"

    override suspend fun fetchUsage(apiKey: String): UsageSnapshot = withContext(Dispatchers.IO) {
        val (code, body) = CodingPlanHttp.get(
            endpoint,
            mapOf(
                "Authorization" to apiKey,
                "Content-Type" to "application/json"
            )
        )
        when (code) {
            200 -> GlmUsageParser.parse(body, System.currentTimeMillis())
            401 -> throw CodingPlanException("API Key 认证失败（401），请确认使用智谱开放平台的 API Key")
            403 -> throw CodingPlanException("API 拒绝访问（403），请检查 Key 权限")
            429 -> throw CodingPlanException("请求过于频繁（429），请稍后重试")
            else -> throw CodingPlanException("查询失败（HTTP $code）")
        }
    }

    override fun parse(json: String, fetchedAtMillis: Long): UsageSnapshot =
        GlmUsageParser.parse(json, fetchedAtMillis)

    companion object {
        const val DEFAULT_ENDPOINT = "https://bigmodel.cn/api/monitor/usage/quota/limit"
    }
}
