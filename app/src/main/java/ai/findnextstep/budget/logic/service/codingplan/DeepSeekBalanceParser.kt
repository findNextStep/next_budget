package ai.findnextstep.budget.logic.service.codingplan

import ai.findnextstep.budget.logic.model.BalanceInfo
import ai.findnextstep.budget.logic.model.UsageSnapshot
import org.json.JSONObject

/**
 * DeepSeek 余额查询接口（GET https://api.deepseek.com/user/balance，官方接口）响应解析器。
 *
 * 响应格式：
 * {"is_available":true,"balance_infos":[
 *   {"currency":"CNY","total_balance":"100.00","granted_balance":"10.00","topped_up_balance":"90.00"}
 * ]}
 */
object DeepSeekBalanceParser {

    fun parse(json: String, fetchedAtMillis: Long): UsageSnapshot {
        val payload = JSONObject(json)
        val balances = mutableListOf<BalanceInfo>()

        val infos = payload.optJSONArray("balance_infos")
        if (infos != null) {
            for (i in 0 until infos.length()) {
                val item = infos.optJSONObject(i) ?: continue
                val currency = item.optString("currency")
                val total = optString(item, "total_balance")
                if (currency.isEmpty() || total == null) continue
                balances.add(
                    BalanceInfo(
                        currency = currency,
                        total = total,
                        granted = optString(item, "granted_balance"),
                        toppedUp = optString(item, "topped_up_balance")
                    )
                )
            }
        }

        return UsageSnapshot(
            provider = "deepseek",
            summary = null,
            limits = emptyList(),
            fetchedAtMillis = fetchedAtMillis,
            balances = balances,
            rawJson = json
        )
    }

    private fun optString(data: JSONObject, key: String): String? {
        if (!data.has(key) || data.isNull(key)) return null
        return data.optString(key).ifEmpty { null }
    }
}
