package ai.findnextstep.budget

import ai.findnextstep.budget.logic.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CategoryTest {

    @Test
    fun `RESTAURANT key is merged into FOOD for legacy data`() {
        assertEquals(Category.FOOD, Category.fromKey("RESTAURANT"))
        assertEquals(Category.FOOD, Category.fromKey("restaurant"))
    }

    @Test
    fun `predefined categories do not contain 餐饮`() {
        assertFalse(Category.predefined.any { it.displayName == "餐饮" })
        assertFalse(Category.expenseCategories.any { it.key == "RESTAURANT" })
    }

    @Test
    fun `unknown key still creates custom category`() {
        val custom = Category.fromKey("奶茶")
        assertEquals("奶茶", custom.displayName)
        assertFalse(custom.isIncome)
    }
}
