package ai.findnextstep.budget.logic.service.codingplan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KimiUsageParserTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `parse data array format with model_name all as summary`() {
        val json = """
        {
          "data": [
            {
              "model_name": "all",
              "used": 320000000,
              "limit": 1000000000,
              "resetTime": "2026-08-31T16:00:00Z"
            },
            {
              "model_name": "kimi-k2",
              "used": 150000,
              "limit": 1000000,
              "reset_in": 18000
            }
          ]
        }
        """.trimIndent()

        val snapshot = KimiUsageParser.parse(json, now)

        assertEquals("kimi", snapshot.provider)
        assertEquals(now, snapshot.fetchedAtMillis)

        val summary = snapshot.summary
        assertNotNull(summary)
        assertEquals("本周用量", summary!!.label)
        assertEquals(320_000_000L, summary.used)
        assertEquals(1_000_000_000L, summary.limit)
        assertEquals(0.32f, summary.percent, 0.001f)
        // ISO 重置时间被解析为绝对毫秒
        assertEquals(Instant_2026_08_31_16_00_UTC, summary.resetAtMillis)

        assertEquals(1, snapshot.limits.size)
        val limit = snapshot.limits[0]
        assertEquals("kimi-k2", limit.label)
        assertEquals(150_000L, limit.used)
        assertEquals(1_000_000L, limit.limit)
        // reset_in 是相对秒数，转换为绝对时间
        assertNotNull(limit.resetAtMillis)
        assertTrue(limit.resetAtMillis!! > System.currentTimeMillis())
    }

    @Test
    fun `parse data array with alternate field names and remaining`() {
        val json = """
        {
          "data": [
            {
              "model_name": "all",
              "used_amount": 500,
              "limit_amount": 2000,
              "reset_at": 1893456000
            },
            {
              "name": "5小时限额",
              "remaining": 300,
              "limit": 1000
            }
          ]
        }
        """.trimIndent()

        val snapshot = KimiUsageParser.parse(json, now)

        val summary = snapshot.summary!!
        assertEquals(500L, summary.used)
        assertEquals(2000L, summary.limit)
        assertEquals(1_893_456_000_000L, summary.resetAtMillis)

        // used 缺失时由 remaining 推导
        val limit = snapshot.limits[0]
        assertEquals("5小时限额", limit.label)
        assertEquals(700L, limit.used)
        assertEquals(1000L, limit.limit)
        assertNull(limit.resetAtMillis)
    }

    @Test
    fun `parse usage plus limits format with window labels`() {
        val json = """
        {
          "usage": {"used": 100, "limit": 1000, "reset_time": 1893456000},
          "limits": [
            {
              "window": {"duration": 5, "timeUnit": "HOUR"},
              "detail": {"used": 50, "limit": 200}
            },
            {
              "window": {"duration": 300, "time_unit": "MINUTE"},
              "detail": {"used": 10, "limit": 100}
            },
            {
              "detail": {"used": 1, "limit": 10}
            }
          ]
        }
        """.trimIndent()

        val snapshot = KimiUsageParser.parse(json, now)

        assertEquals("本周用量", snapshot.summary!!.label)
        assertEquals(100L, snapshot.summary!!.used)

        assertEquals(3, snapshot.limits.size)
        assertEquals("5小时限额", snapshot.limits[0].label)
        assertEquals("5小时限额", snapshot.limits[1].label) // 300 分钟归一化为 5 小时
        assertEquals("限额 #3", snapshot.limits[2].label)
    }

    @Test
    fun `empty payload yields empty snapshot`() {
        val snapshot = KimiUsageParser.parse("{}", now)
        assertNull(snapshot.summary)
        assertTrue(snapshot.limits.isEmpty())
    }

    @Test
    fun `items without used or limit are skipped`() {
        val json = """{"data": [{"model_name": "kimi-k2"}, {"model_name": "all", "used": 1, "limit": 2}]}"""
        val snapshot = KimiUsageParser.parse(json, now)
        assertNotNull(snapshot.summary)
        assertTrue(snapshot.limits.isEmpty())
    }

    companion object {
        // 2026-08-31T16:00:00Z
        private const val Instant_2026_08_31_16_00_UTC = 1_788_192_000_000L
    }
}
