package com.hebi.homecrew.ui.model

import kotlin.math.roundToInt

enum class ChoreStatus(val label: String) {
    PLANNED("예정"),
    COMPLETED("완료"),
    SKIPPED("건너뜀"),
    OVERDUE("기한 초과")
}

data class PreviewChore(
    val id: Int,
    val title: String,
    val detail: String,
    val status: ChoreStatus
)

fun completionPercent(completed: Int, total: Int): Int {
    if (total <= 0) return 0
    return (completed.coerceIn(0, total).toDouble() * 100.0 / total).roundToInt()
}
