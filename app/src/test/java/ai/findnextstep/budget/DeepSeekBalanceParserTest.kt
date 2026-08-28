package ai.findnextstep.budget.logic.service.codingplan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekBalanceParserTest {

    private val now = 1_788_000_000_000L

    @Test
    fun `parse multi currency balances`() {
        val json = """
        {
          "is_available": true,
          "balance_infos": [
            {"currency": "CNY", "total_balance": "100.50", "granted_balance": "10.00", "topped_up_balance": "90.50"},
            {"currency": "USD", "total_balance": "1.00", "granted_balance": "0.00", "topped_up_balance": "1.00"}
          ]
        }
        """.trimIndent()

        val snapshot = DeepSeekBalanceParser.parse(json, now)

        assertEquals("deepseek", snapshot.provider)
        assertEquals(now, snapshot.fetchedAtMillis)
        assertNull(snapshot.summary)
        assertTrue(snapshot.limits.isEmpty())
        assertEquals(2, snapshot.balances.size)

        val cny = snapshot.balances[0]
        assertEquals("CNY", cny.currency)
        assertEquals("100.50", cny.total)
        assertEquals("10.00", cny.granted)
        assertEquals("90.50", cny.toppedUp)
    }

    @Test
    fun `missing optional fields are tolerated`() {
        val json = """{"balance_infos": [{"currency": "CNY", "total_balance": "5.00"}]}"""
        val snapshot = DeepSeekBalanceParser.parse(json, now)
        assertEquals(1, snapshot.balances.size)
        assertEquals("5.00", snapshot.balances[0].total)
        assertNull(snapshot.balances[0].granted)
    }

    @Test
    fun `entry without total balance is skipped`() {
        val json = """{"balance_infos": [{"currency": "CNY"}, {"currency": "USD", "total_balance": "2.00"}]}"""
        val snapshot = DeepSeekBalanceParser.parse(json, now)
        assertEquals(1, snapshot.balances.size)
        assertEquals("USD", snapshot.balances[0].currency)
    }

    @Test
    fun `empty payload yields empty balances`() {
        val snapshot = DeepSeekBalanceParser.parse("{}", now)
        assertTrue(snapshot.balances.isEmpty())
    }
}
