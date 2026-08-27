package ai.findnextstep.budget.logic.service.codingplan

import ai.findnextstep.budget.logic.model.UsageSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

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

    override suspend fun fetchUsage(apiKey: String): UsageSnapshot = withContext(Dispatchers.IO) {
        val body = get("$baseUrl/usages", apiKey)
            ?: get("$baseUrl/usage", apiKey) // 兼容旧路径
            ?: throw CodingPlanException("未找到用量接口（404），接口可能已变更")
        KimiUsageParser.parse(body, System.currentTimeMillis())
    }

    /** 成功返回响应体；404 返回 null 以尝试回退路径；其他错误抛异常 */
    private fun get(url: String, apiKey: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("User-Agent", "KimiCLI/1.6")
        }
        try {
            when (val code = conn.responseCode) {
                200 -> return conn.inputStream.bufferedReader().use { it.readText() }
                404 -> return null
                401 -> throw CodingPlanException("API Key 认证失败（401），请确认使用 Kimi Code 控制台的 sk-kimi- 开头 Key")
                403 -> throw CodingPlanException("API 拒绝访问（403），请检查 Key 权限")
                429 -> throw CodingPlanException("请求过于频繁（429），请稍后重试")
                else -> throw CodingPlanException("查询失败（HTTP $code）")
            }
        } catch (e: CodingPlanException) {
            throw e
        } catch (e: java.io.IOException) {
            throw CodingPlanException("网络错误：${e.message ?: "请检查网络连接"}")
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.kimi.com/coding/v1"
    }
}
