package ai.findnextstep.budget.logic.model

/**
 * 账目类型，支持预定义类型和自定义类型。
 * [key] 为唯一标识，[displayName] 为显示名称，[isIncome] 标记是否为收入类型，
 * [emoji] 为展示用图标，自定义类型为空字符串。
 */
data class Category(
    val key: String,
    val displayName: String,
    val isIncome: Boolean = false,
    val emoji: String = ""
) {
    /** 带 emoji 的展示文本，无 emoji 时仅名称 */
    val label: String
        get() = if (emoji.isEmpty()) displayName else "$emoji $displayName"

    companion object {
        // ── 收入类 ──
        val SALARY = Category("SALARY", "薪水", isIncome = true, emoji = "💰")
        val AUTOMATIC = Category("AUTOMATIC", "自动入账", isIncome = true, emoji = "🤖")
        val BONUS = Category("BONUS", "奖金", isIncome = true, emoji = "🎉")
        val INVESTMENT = Category("INVESTMENT", "投资", isIncome = true, emoji = "📈")
        val REFUND = Category("REFUND", "退款", isIncome = true, emoji = "💸")

        // ── 支出类 ──
        val FOOD = Category("FOOD", "主食", isIncome = false, emoji = "🍚")
        val SNACK = Category("SNACK", "零食", isIncome = false, emoji = "🍪")
        val RENT = Category("RENT", "房租", isIncome = false, emoji = "🏠")
        val TRAFFIC = Category("TRAFFIC", "交通", isIncome = false, emoji = "🚌")
        val SHOPPING = Category("SHOPPING", "购物", isIncome = false, emoji = "🛒")
        val PARTY = Category("PARTY", "饮料", isIncome = false, emoji = "🥤")
        val GIFT = Category("GIFT", "礼品", isIncome = false, emoji = "🎁")
        val DONATION = Category("DONATION", "游戏", isIncome = false, emoji = "🎮")
        val ENTERTAINMENT = Category("ENTERTAINMENT", "娱乐", isIncome = false, emoji = "🎬")
        val MEDICAL = Category("MEDICAL", "医疗", isIncome = false, emoji = "💊")
        val EDUCATION = Category("EDUCATION", "教育", isIncome = false, emoji = "📚")
        val UTILITIES = Category("UTILITIES", "水电", isIncome = false, emoji = "💡")
        val OTHER = Category("OTHER", "其他", isIncome = false, emoji = "📦")

        /** 所有预定义类型 */
        val predefined: List<Category> = listOf(
            SALARY, AUTOMATIC, BONUS, INVESTMENT, REFUND,
            FOOD, SNACK, RENT, TRAFFIC, SHOPPING,
            PARTY, GIFT, DONATION, ENTERTAINMENT,
            MEDICAL, EDUCATION, UTILITIES, OTHER
        )

        /** 支出类型 */
        val expenseCategories: List<Category> = predefined.filter { !it.isIncome }

        /** 收入类型 */
        val incomeCategories: List<Category> = predefined.filter { it.isIncome }

        /** 根据 key 查找预定义类型，找不到则创建自定义类型 */
        fun fromKey(key: String): Category {
            // 兼容：已删除的「餐饮」合并到「主食」
            if (key.equals("RESTAURANT", ignoreCase = true)) return FOOD
            return predefined.find { it.key.equals(key, ignoreCase = true) }
                ?: Category(key.uppercase(), key, isIncome = false)
        }
    }
}
