package ai.findnextstep.budget.logic.model

/**
 * 账目类型，支持预定义类型和自定义类型。
 * [key] 为唯一标识，[displayName] 为显示名称，[isIncome] 标记是否为收入类型。
 */
data class Category(
    val key: String,
    val displayName: String,
    val isIncome: Boolean = false
) {
    companion object {
        // ── 收入类 ──
        val SALARY = Category("SALARY", "薪水", isIncome = true)
        val AUTOMATIC = Category("AUTOMATIC", "自动入账", isIncome = true)
        val BONUS = Category("BONUS", "奖金", isIncome = true)
        val INVESTMENT = Category("INVESTMENT", "投资", isIncome = true)
        val REFUND = Category("REFUND", "退款", isIncome = true)

        // ── 支出类 ──
        val FOOD = Category("FOOD", "主食", isIncome = false)
        val SNACK = Category("SNACK", "零食", isIncome = false)
        val RENT = Category("RENT", "房租", isIncome = false)
        val TRAFFIC = Category("TRAFFIC", "交通", isIncome = false)
        val SHOPPING = Category("SHOPPING", "购物", isIncome = false)
        val RESTAURANT = Category("RESTAURANT", "餐饮", isIncome = false)
        val PARTY = Category("PARTY", "饮料", isIncome = false)
        val GIFT = Category("GIFT", "礼品", isIncome = false)
        val DONATION = Category("DONATION", "游戏", isIncome = false)
        val ENTERTAINMENT = Category("ENTERTAINMENT", "娱乐", isIncome = false)
        val MEDICAL = Category("MEDICAL", "医疗", isIncome = false)
        val EDUCATION = Category("EDUCATION", "教育", isIncome = false)
        val UTILITIES = Category("UTILITIES", "水电", isIncome = false)
        val OTHER = Category("OTHER", "其他", isIncome = false)

        /** 所有预定义类型 */
        val predefined: List<Category> = listOf(
            SALARY, AUTOMATIC, BONUS, INVESTMENT, REFUND,
            FOOD, SNACK, RENT, TRAFFIC, SHOPPING,
            RESTAURANT, PARTY, GIFT, DONATION, ENTERTAINMENT,
            MEDICAL, EDUCATION, UTILITIES, OTHER
        )

        /** 支出类型 */
        val expenseCategories: List<Category> = predefined.filter { !it.isIncome }

        /** 收入类型 */
        val incomeCategories: List<Category> = predefined.filter { it.isIncome }

        /** 根据 key 查找预定义类型，找不到则创建自定义类型 */
        fun fromKey(key: String): Category {
            return predefined.find { it.key.equals(key, ignoreCase = true) }
                ?: Category(key.uppercase(), key, isIncome = false)
        }
    }
}
