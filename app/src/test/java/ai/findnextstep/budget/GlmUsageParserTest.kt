package ai.findnextstep.budget.logic.service.codingplan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlmUsageParserTest {

    private val now = 1_788_000_000_000L

    @Test
    fun `parse limits with five hour weekly and mcp windows`() {
        val json = """
        {
          "code": 200,
          "msg": "ok",
          "success": true,
          "data": {
            "limits": [
              {"type": "TOKENS_LIMIT", "unit": 3, "number": 1000000, "usage": 320000, "percentage": 32, "nextResetTime": 1788192000000},
              {"type": "TOKENS_LIMIT", "unit": 6, "number": 5000000, "usage": 1000000, "percentage": 20, "nextResetTime": 1788992000000},
              {"type": "TIME_LIMIT", "unit": 5, "number": 100, "usage": 5, "percentage": 5}
            ]
          }
        }
        """.trimIndent()

        val snapshot = GlmUsageParser.parse(json, now)

        assertEquals("glm", snapshot.provider)
        assertEquals(now, snapshot.fetchedAtMillis)
        assertNull(snapshot.summary)
        assertEquals(3, snapshot.limits.size)

        val fiveHour = snapshot.limits[0]
        assertEquals("5小时限额", fiveHour.label)
        assertEquals(320_000L, fiveHour.used)
        assertEquals(1_000_000L, fiveHour.limit)
        assertEquals(0.32f, fiveHour.percent, 0.001f)
        // GLM 的 nextResetTime 是毫秒时间戳，直接使用
        assertEquals(1_788_192_000_000L, fiveHour.resetAtMillis)

        val weekly = snapshot.limits[1]
        assertEquals("本周限额", weekly.label)
        assertEquals(0.20f, weekly.percent, 0.001f)

        val mcp = snapshot.limits[2]
        assertEquals("MCP 工具额度", mcp.label)
        assertNull(mcp.resetAtMillis)
    }

    @Test
    fun `percentage takes precedence over used limit ratio`() {
        val json = """
        {"data": {"limits": [
          {"type": "TOKENS_LIMIT", "unit": 3, "number": 0, "usage": 0, "percentage": 47}
        ]}}
        """.trimIndent()

        val snapshot = GlmUsageParser.parse(json, now)
        assertEquals(0.47f, snapshot.limits[0].percent, 0.001f)
    }

    @Test
    fun `unknown limit type gets generic label`() {
        val json = """{"data": {"limits": [{"type": "OTHER", "unit": 9, "percentage": 10}]}}"""
        val snapshot = GlmUsageParser.parse(json, now)
        assertEquals("限额", snapshot.limits[0].label)
    }

    @Test
    fun `empty limits yields empty snapshot`() {
        val snapshot = GlmUsageParser.parse("""{"data": {"limits": []}}""", now)
        assertTrue(snapshot.limits.isEmpty())
        assertTrue(snapshot.balances.isEmpty())
    }

    @Test
    fun `currentValue used as fallback for used`() {
        val json = """{"data": {"limits": [{"type": "TOKENS_LIMIT", "unit": 3, "number": 100, "currentValue": 42, "percentage": 42}]}}"""
        val snapshot = GlmUsageParser.parse(json, now)
        assertEquals(42L, snapshot.limits[0].used)
    }
}
