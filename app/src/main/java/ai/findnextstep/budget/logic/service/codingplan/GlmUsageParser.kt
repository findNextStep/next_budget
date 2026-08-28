package ai.findnextstep.budget.logic.service.codingplan

import ai.findnextstep.budget.logic.model.UsageLimit
import ai.findnextstep.budget.logic.model.UsageSnapshot
import org.json.JSONObject

/**
 * GLM Coding Plan（智谱 bigmodel.cn）用量接口响应解析器。
 *
 * 接口为未公开接口（GET https://bigmodel.cn/api/monitor/usage/quota/limit），响应格式：
 * {"code":200,"msg":"ok","success":true,"data":{"limits":[
 *   {"type":"TOKENS_LIMIT","unit":3,"number":1000,"usage":320,"percentage":32,"nextResetTime":1788192000000},
 *   ...
 * ]}}
 *
 * 窗口约定：type=TOKENS_LIMIT 且 unit=3 → 5 小时窗；unit=6 → 周窗；type=TIME_LIMIT → MCP 工具额度。
 * percentage 为已用百分比（0~100）；nextResetTime 为毫秒时间戳。
 */
object GlmUsageParser {

    fun parse(json: String, fetchedAtMillis: Long): UsageSnapshot {
        val payload = JSONObject(json)
        val limits = mutableListOf<UsageLimit>()

        val rawLimits = payload.optJSONObject("data")?.optJSONArray("limits")
        if (rawLimits != null) {
            for (i in 0 until rawLimits.length()) {
                val item = rawLimits.optJSONObject(i) ?: continue
                limits.add(toUsageLimit(item))
            }
        }

        return UsageSnapshot(
            provider = "glm",
            summary = null,
            limits = limits,
            fetchedAtMillis = fetchedAtMillis,
            rawJson = json
        )
    }

    private fun toUsageLimit(data: JSONObject): UsageLimit {
        val type = data.optString("type")
        val unit = data.optInt("unit", -1)
        val label = when {
            type == "TOKENS_LIMIT" && unit == 3 -> "5小时限额"
            type == "TOKENS_LIMIT" && unit == 6 -> "本周限额"
            type == "TIME_LIMIT" -> "MCP 工具额度"
            else -> "限额"
        }
        val percentage = if (data.has("percentage")) data.optDouble("percentage", 0.0) / 100.0 else null
        val resetAt = if (data.has("nextResetTime") && !data.isNull("nextResetTime")) {
            data.optLong("nextResetTime", 0L).takeIf { it > 0 }
        } else null
        return UsageLimit(
            label = label,
            used = optLong(data, "usage") ?: optLong(data, "currentValue") ?: 0L,
            limit = optLong(data, "number") ?: 0L,
            resetAtMillis = resetAt,
            percentOverride = percentage?.toFloat()
        )
    }

    private fun optLong(data: JSONObject, key: String): Long? {
        if (!data.has(key) || data.isNull(key)) return null
        return when (val v = data.opt(key)) {
            is Number -> v.toLong()
            is String -> v.toLongOrNull()
            else -> null
        }
    }
}
