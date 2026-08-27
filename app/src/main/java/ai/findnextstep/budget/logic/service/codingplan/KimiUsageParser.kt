package ai.findnextstep.budget.logic.service.codingplan

import ai.findnextstep.budget.logic.model.UsageLimit
import ai.findnextstep.budget.logic.model.UsageSnapshot
import org.json.JSONObject
import java.time.Instant

/**
 * Kimi Code（Coding Plan）用量接口响应解析器。
 *
 * 接口为未公开接口（GET https://api.kimi.com/coding/v1/usages），响应结构存在两种已知格式，
 * 且字段命名不统一，这里尽量兼容：
 *  - {"data": [{model_name, used/used_amount, limit/limit_amount, remaining, resetTime/reset_at, ...}]}
 *  - {"usage": {...}, "limits": [{window: {duration, timeUnit}, detail: {...}}]}
 */
object KimiUsageParser {

    fun parse(json: String, fetchedAtMillis: Long): UsageSnapshot {
        val payload = JSONObject(json)
        var summary: UsageLimit? = null
        val limits = mutableListOf<UsageLimit>()

        val data = payload.optJSONArray("data")
        if (data != null) {
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val isOverall = item.optString("model_name") == "all"
                val row = toUsageLimit(item, if (isOverall) "本周用量" else "限额", useModelName = !isOverall)
                if (row != null) {
                    if (isOverall) summary = row else limits.add(row)
                }
            }
        } else {
            payload.optJSONObject("usage")?.let {
                summary = toUsageLimit(it, "本周用量")
            }
            val rawLimits = payload.optJSONArray("limits")
            if (rawLimits != null) {
                for (i in 0 until rawLimits.length()) {
                    val item = rawLimits.optJSONObject(i) ?: continue
                    val detail = item.optJSONObject("detail") ?: item
                    val window = item.optJSONObject("window")
                    val row = toUsageLimit(detail, limitLabel(window, i))
                    if (row != null) limits.add(row)
                }
            }
        }

        return UsageSnapshot(
            provider = "kimi",
            summary = summary,
            limits = limits,
            fetchedAtMillis = fetchedAtMillis,
            rawJson = json
        )
    }

    private fun toUsageLimit(data: JSONObject, defaultLabel: String, useModelName: Boolean = true): UsageLimit? {
        val limit = optLong(data, "limit") ?: optLong(data, "limit_amount")
        var used = optLong(data, "used") ?: optLong(data, "used_amount")
        if (used == null) {
            val remaining = optLong(data, "remaining")
            if (remaining != null && limit != null) used = limit - remaining
        }
        if (used == null && limit == null) return null
        return UsageLimit(
            label = data.optString("name").ifEmpty {
                data.optString("title").ifEmpty {
                    if (useModelName) data.optString("model_name").ifEmpty { defaultLabel } else defaultLabel
                }
            },
            used = used ?: 0L,
            limit = limit ?: 0L,
            resetAtMillis = parseResetAt(data)
        )
    }

    /** 解析重置时间，兼容 ISO 字符串 / epoch 秒 / reset_in 秒数，返回绝对毫秒时间 */
    private fun parseResetAt(data: JSONObject): Long? {
        val raw = when {
            data.has("resetTime") -> data.opt("resetTime")
            data.has("reset_at") -> data.opt("reset_at")
            data.has("reset_time") -> data.opt("reset_time")
            else -> null
        }
        when (raw) {
            is Number -> return raw.toLong() * 1000L
            is String -> {
                if (raw.isNotEmpty()) {
                    try {
                        return Instant.parse(raw.replace("Z", "+00:00").let {
                            // Instant.parse 只接受带 Z 的格式，做简单归一化
                            if (it.endsWith("+00:00")) it.substring(0, it.length - 6) + "Z" else it
                        }).toEpochMilli()
                    } catch (_: Exception) {
                        // 尝试按 epoch 秒字符串解析
                        raw.toLongOrNull()?.let { return it * 1000L }
                    }
                }
            }
        }
        val resetIn = optLong(data, "reset_in")
        if (resetIn != null) return System.currentTimeMillis() + resetIn * 1000L
        return null
    }

    /** 根据窗口时长生成限额标签，如「5小时限额」「7天限额」 */
    private fun limitLabel(window: JSONObject?, index: Int): String {
        val duration = window?.let { optLong(it, "duration") }?.toInt()
        val timeUnit = window?.optString("timeUnit")?.ifEmpty { window.optString("time_unit") }
            ?.uppercase() ?: ""
        if (duration != null) {
            when {
                timeUnit.contains("MINUTE") -> {
                    return if (duration >= 60 && duration % 60 == 0) "${duration / 60}小时限额"
                    else "${duration}分钟限额"
                }
                timeUnit.contains("HOUR") -> return "${duration}小时限额"
                timeUnit.contains("DAY") -> return "${duration}天限额"
                timeUnit.contains("MONTH") -> return "${duration}个月限额"
            }
        }
        return "限额 #${index + 1}"
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
