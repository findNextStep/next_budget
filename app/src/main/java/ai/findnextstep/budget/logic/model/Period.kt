package ai.findnextstep.budget.logic.model

/**
 * 统计时间维度。
 */
enum class Period(val label: String) {
    DAY("日"),
    WEEK("周"),
    MONTH("月"),
    YEAR("年");
}
