package com.hebi.homecrew.ui.screens

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun selectedChoreOccurrence(
    occurrences: List<DemoChore>,
    selectedId: String?,
    today: LocalDate
): DemoChore? {
    val ordered = occurrences.sortedWith(compareBy({ it.date }, { it.definition.time }, { it.id }))
    return ordered.firstOrNull { it.id == selectedId }
        ?: ordered.firstOrNull { it.date == today }
        ?: ordered.firstOrNull { !it.date.isBefore(today) }
        ?: ordered.lastOrNull()
}

internal fun upcomingChoreOccurrences(
    occurrences: List<DemoChore>,
    today: LocalDate
): List<DemoChore> = occurrences
    .filter { !it.completed && !it.skipped && !it.date.isBefore(today) }
    .sortedWith(compareBy({ it.date }, { it.definition.time }, { it.id }))
    .take(3)

internal fun missedChoreOccurrences(
    occurrences: List<DemoChore>,
    today: LocalDate
): List<DemoChore> = occurrences
    .filter { !it.completed && !it.skipped && it.date.isBefore(today) }
    .sortedWith(compareByDescending<DemoChore> { it.date }
        .thenByDescending { it.definition.time }.thenBy { it.id })
    .take(3)

internal fun recentCompletedChoreOccurrences(
    occurrences: List<DemoChore>
): List<DemoChore> = occurrences
    .filter { it.completed }
    .sortedWith(
        compareByDescending<DemoChore> { it.completedAtEpochMillis ?: Long.MIN_VALUE }
            .thenByDescending { it.date }
            .thenBy { it.id }
    )
    .take(3)

internal fun recordedCompletionTime(
    epochMillis: Long?,
    zoneId: ZoneId
): String? = epochMillis?.let {
    Instant.ofEpochMilli(it).atZone(zoneId)
        .format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm", Locale.KOREAN))
}
