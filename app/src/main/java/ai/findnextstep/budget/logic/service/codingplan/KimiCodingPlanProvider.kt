package ai.findnextstep.budget.logic.service.codingplan

import ai.findnextstep.budget.logic.model.UsageSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Kimi Code（Coding Plan 订阅）用量查询。
 *
 * 接口为 Kimi Code 平台未公开接口，响应结构可能变动，解析逻辑见 [KimiUsageParser]。
 * 需要使用 Kimi Code 控制台创建的 API Key（sk-kimi- 开头），与开放平台（platform.kimi.com）的 Key 不通用。
 */
class KimiCodingPlanProvider(
    private val baseUrl: String = DEFAULT_BASE_URL
) : CodingPlanProvider {

    override val id: String = "kimi"
    override val displayName: String = "Kimi Code"

    override suspend fun fetchUsage(apiKey: String): UsageSnapshot = withContext(Dispatchers.IO) {
        val headers = mapOf(
            "Authorization" to "Bearer $apiKey",
            "User-Agent" to "KimiCLI/1.6"
        )
        val (code, body) = CodingPlanHttp.get("$baseUrl/usages", headers)
        when (code) {
            200 -> KimiUsageParser.parse(body, System.currentTimeMillis())
            404 -> fetchFallback(headers)
            401 -> throw CodingPlanException("API Key 认证失败（401），请确认使用 Kimi Code 控制台的 sk-kimi- 开头 Key")
            403 -> throw CodingPlanException("API 拒绝访问（403），请检查 Key 权限")
            429 -> throw CodingPlanException("请求过于频繁（429），请稍后重试")
            else -> throw CodingPlanException("查询失败（HTTP $code）")
        }
    }

    /** 兼容旧路径 /usage */
    private fun fetchFallback(headers: Map<String, String>): UsageSnapshot {
        val (code, body) = CodingPlanHttp.get("$baseUrl/usage", headers)
        if (code != 200) throw CodingPlanException("未找到用量接口（$code），接口可能已变更")
        return KimiUsageParser.parse(body, System.currentTimeMillis())
    }

    override fun parse(json: String, fetchedAtMillis: Long): UsageSnapshot =
        KimiUsageParser.parse(json, fetchedAtMillis)

    companion object {
        const val DEFAULT_BASE_URL = "https://api.kimi.com/coding/v1"
    }
}
