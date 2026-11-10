package com.hebi.homecrew.ui.screens

import java.time.LocalDate

private val weekdayNames = listOf("월", "화", "수", "목", "금", "토", "일")

fun weekdayLabel(day: LocalDate): String = weekdayNames[day.dayOfWeek.value - 1]

fun koreanDateLabel(day: LocalDate): String =
    "${day.monthValue}월 ${day.dayOfMonth}일 (${weekdayLabel(day)})"

fun koreanMonthLabel(day: LocalDate): String = "${day.year}년 ${day.monthValue}월"
