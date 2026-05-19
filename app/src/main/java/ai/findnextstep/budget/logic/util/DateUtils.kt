package ai.findnextstep.budget.logic.util

import java.time.LocalDate
import java.time.ZoneId

fun LocalDate.dayEpochMillisRange(zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
    val start = atStartOfDay(zone).toInstant().toEpochMilli()
    val end = plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    return start to end
}
