package ai.findnextstep.budget.logic.service.codingplan

import java.net.HttpURLConnection
import java.net.URL

/**
 * Coding Plan 各平台共用的简单 HTTP GET。
 * @return 状态码 + 响应体
 * @throws CodingPlanException 网络错误
 */
internal object CodingPlanHttp {

    fun get(url: String, headers: Map<String, String>, timeoutMillis: Int = 10_000): Pair<Int, String> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return code to body
        } catch (e: java.io.IOException) {
            throw CodingPlanException("网络错误：${e.message ?: "请检查网络连接"}")
        } finally {
            conn.disconnect()
        }
    }
}
