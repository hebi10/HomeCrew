package com.hebi.homecrew.data.cloud

import com.hebi.homecrew.data.local.HomeCrewRows
import com.hebi.homecrew.ui.setup.HouseholdSetup
import com.hebi.homecrew.ui.setup.validateHouseholdSetup
import java.security.MessageDigest
import java.time.LocalDate

internal data class LocalMigrationSource(val household: HouseholdSetup, val rows: HomeCrewRows)

internal fun migrationMemberNames(source: LocalMigrationSource): List<String> =
    (source.household.members + source.household.defaultMember +
        source.rows.definitions.map { it.defaultOwner } +
        source.rows.members.map { it.memberName } +
        source.rows.occurrences.map { it.assignedMember } +
        source.rows.occurrences.mapNotNull { it.completedBy })
        .distinct().sorted()

internal data class LocalMigrationManifest(
    val householdId: String,
    val ownerUid: String,
    val definitions: List<Map<String, Any?>>,
    val occurrences: List<Map<String, Any?>>,
    val mappedMemberUids: List<String>,
    val defaultMemberUid: String,
    val legacyWorkloadCount: Int
) {
    val digest: String get() = migrationDigest(payload())

    fun payload(): Map<String, Any?> = mapOf(
        "version" to 1, "householdId" to householdId, "ownerUid" to ownerUid,
        "definitions" to definitions, "occurrences" to occurrences,
        "memberUids" to mappedMemberUids, "defaultMemberUid" to defaultMemberUid,
        "legacyWorkloadCount" to legacyWorkloadCount
    )
}

internal data class LocalMigrationPlan(
    val definitionCount: Int,
    val occurrenceCount: Int,
    val completedCount: Int,
    val skippedCount: Int,
    val requiredNames: List<String>,
    val blockers: List<String>,
    val manifest: LocalMigrationManifest?
)

private val migrationDateRange = LocalDate.of(1900, 1, 1).toEpochDay()..
    LocalDate.of(2200, 12, 31).toEpochDay()

private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
    .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xff) }

internal fun migratedDefinitionId(sourceId: String): String =
    sha256("homecrew-local-definition-v1:$sourceId")

internal fun migratedOccurrenceId(definitionId: String, date: Long): String =
    sha256("homecrew-occurrence-v1:$definitionId:$date")

// Canonical digest with typed, length-delimited strings and sorted map keys.
// No locale-dependent formatting, iteration order or JSON escaping ambiguity.
internal fun migrationDigest(value: Any?): String {
    val digest = MessageDigest.getInstance("SHA-256")
    fun write(text: String) { digest.update(text.toByteArray(Charsets.UTF_8)) }
    fun add(item: Any?) {
        when (item) {
            null -> write("n;")
            is Boolean -> write(if (item) "b1;" else "b0;")
            is Int, is Long -> write("i$item;")
            is String -> {
                write("s" + item.toByteArray(Charsets.UTF_8).size + ":")
                write(item)
            }
            is List<*> -> {
                write("l" + item.size + ":")
                item.forEach(::add)
            }
            is Map<*, *> -> {
                val keys = item.keys.map { it as String }.sorted()
                write("m" + keys.size + ":")
                keys.forEach { key -> add(key); add(item[key]) }
            }
            else -> error("Unsupported migration value")
        }
    }
    add(value)
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

/** Local dry-run. The target snapshot must be current, server-verified and empty. */
internal fun prepareLocalMigration(
    source: LocalMigrationSource,
    householdId: String,
    accountUid: String,
    target: RemoteSnapshot,
    mapping: Map<String, String>
): LocalMigrationPlan {
    val rows = source.rows
    val names = migrationMemberNames(source)
    val blockers = linkedSetOf<String>()
    val byId = rows.definitions.associateBy { it.id }
    val assignments = rows.members.groupBy { it.definitionId }
    val verifiedUids = target.members.map { it.uid }.toSet()
    if (target.mayBeStale || target.members.none { it.uid == accountUid && it.isOwner } ||
        target.members.map { it.uid }.distinct().size != target.members.size ||
        target.members.count { it.isOwner } != 1
    ) blockers += "서버에서 확인된 공동 공간 소유자만 이전을 준비할 수 있습니다."
    if (target.chores.isNotEmpty() || target.occurrences.isNotEmpty())
        blockers += "기존 공동 집안일이 있는 공간에는 자동으로 합치지 않습니다."
    if (!validateHouseholdSetup(source.household))
        blockers += "로컬 공동 공간 설정이 올바르지 않습니다."
    if (runCatching { requireValidCloudId(householdId) }.isFailure)
        blockers += "공동 공간 식별자가 올바르지 않습니다."
    if (rows.definitions.isEmpty()) blockers += "이전할 집안일이 없습니다."
    if (rows.definitions.size > 2_000 || rows.members.size > 10_000 || rows.occurrences.size > 100_000)
        blockers += "지원되는 로컬 백업 범위를 초과했습니다."
    if (names.any { it.isBlank() } || names.size > 8)
        blockers += "현재 공동 공간의 구성원 범위로 매핑할 수 없는 로컬 이름이 있습니다."
    if (mapping.keys.any { it !in names } || names.any { mapping[it] !in verifiedUids } ||
        names.mapNotNull(mapping::get).distinct().size != names.size)
        blockers += "모든 로컬 구성원을 서로 다른 현재 공동 공간 계정에 직접 연결해 주세요."
    if (byId.size != rows.definitions.size || rows.definitions.any { it.id.isBlank() || it.id.length > 512 } ||
        rows.members.any { it.definitionId !in byId || it.position < 0 } ||
        rows.occurrences.any { it.definitionId !in byId || it.id.isBlank() || it.id.length > 1024 } ||
        rows.occurrences.map { it.id }.distinct().size != rows.occurrences.size ||
        rows.occurrences.map { it.definitionId to it.scheduledEpochDay }.distinct().size != rows.occurrences.size)
        blockers += "중복되거나 연결이 끊긴 로컬 기록이 있습니다."

    fun schedule(definition: com.hebi.homecrew.data.local.ChoreDefinitionEntity): RemoteSchedule {
        require(definition.weekdayMask in 0..127)
        val members = assignments[definition.id].orEmpty().sortedBy { it.position }
        require(members.map { it.position } == members.indices.toList())
        require(members.isNotEmpty() && members.map { it.memberName }.distinct().size == members.size)
        require(definition.defaultOwner in members.map { it.memberName })
        return RemoteSchedule(
            startEpochDay = requireNotNull(definition.startEpochDay),
            repeatMode = definition.repeatMode,
            weekdays = if (definition.repeatMode in setOf("WEEKLY", "BIWEEKLY"))
                (1..7).filter { definition.weekdayMask and (1 shl (it - 1)) != 0 } else emptyList(),
            intervalDays = if (definition.repeatMode == "CUSTOM") definition.repeatIntervalDays else 2,
            assignmentMode = definition.assignmentMode,
            memberUids = members.map { requireNotNull(mapping[it.memberName]) }
        )
    }

    val definitionPayload = mutableListOf<Map<String, Any?>>()
    if (blockers.isEmpty()) {
        for (definition in rows.definitions.sortedBy { it.id }) {
            val result = runCatching {
                require(definition.name.isNotBlank() && definition.name.length <= 40 &&
                    definition.category.isNotBlank() && definition.category.length <= 64 &&
                    definition.description.length <= 300 && definition.repeatLabel.length <= 128 &&
                    definition.points in 1..5 && definition.repeatIntervalDays in 1..30 &&
                    definition.scheduledTime.matches(Regex("([01][0-9]|2[0-3]):[0-5][0-9]")) &&
                    definition.createdAtEpochMillis >= 0 &&
                    definition.updatedAtEpochMillis >= definition.createdAtEpochMillis)
                val rule = schedule(definition)
                mapOf<String, Any?>(
                    "id" to migratedDefinitionId(definition.id), "sourceLocalId" to definition.id,
                    "name" to definition.name, "category" to definition.category,
                    "description" to definition.description, "points" to definition.points,
                    "assigneeUid" to requireNotNull(mapping[definition.defaultOwner]),
                    "scheduledTime" to definition.scheduledTime, "repeatLabel" to definition.repeatLabel,
                    "schedule" to rule.payload(), "sourceWeekdayMask" to definition.weekdayMask,
                    "sourceRepeatIntervalDays" to definition.repeatIntervalDays,
                    "sourceCreatedAtEpochMillis" to definition.createdAtEpochMillis,
                    "sourceUpdatedAtEpochMillis" to definition.updatedAtEpochMillis
                )
            }
            result.getOrNull()?.let(definitionPayload::add)
                ?: run { blockers += "반복 규칙·예정 시각·담당자 또는 날짜가 유효하지 않은 집안일이 있습니다." }
        }
    }

    val occurrencePayload = mutableListOf<Map<String, Any?>>()
    if (blockers.isEmpty()) {
        for (occurrence in rows.occurrences.sortedWith(compareBy({ it.definitionId }, { it.scheduledEpochDay }, { it.id }))) {
            val result = runCatching {
                require(occurrence.scheduledEpochDay in migrationDateRange &&
                    occurrence.workloadPoints in 0..5 &&
                    !(occurrence.completed && occurrence.skippedAtEpochMillis != null) &&
                    (occurrence.skippedAtEpochMillis != null || occurrence.skipReason == null) &&
                    (occurrence.skipReason == null || occurrence.skipReason.length <= 120) &&
                    listOfNotNull(occurrence.completedAtEpochMillis, occurrence.skippedAtEpochMillis).all { it >= 0 } &&
                    (occurrence.completed || (occurrence.completedBy == null && occurrence.completedAtEpochMillis == null)))
                val definition = requireNotNull(byId[occurrence.definitionId])
                val remoteId = migratedDefinitionId(definition.id)
                mapOf<String, Any?>(
                    "id" to migratedOccurrenceId(remoteId, occurrence.scheduledEpochDay),
                    "sourceLocalId" to occurrence.id, "definitionId" to remoteId,
                    "scheduledEpochDay" to occurrence.scheduledEpochDay,
                    "assignedUid" to requireNotNull(mapping[occurrence.assignedMember]),
                    "workloadPoints" to (occurrence.workloadPoints.takeIf { it > 0 } ?: definition.points),
                    "sourceWorkloadPoints" to occurrence.workloadPoints,
                    "completed" to occurrence.completed,
                    "completedByUid" to occurrence.completedBy?.let { requireNotNull(mapping[it]) },
                    "completedAtEpochMillis" to occurrence.completedAtEpochMillis,
                    "skippedAtEpochMillis" to occurrence.skippedAtEpochMillis,
                    "skipReason" to occurrence.skipReason
                )
            }
            result.getOrNull()?.let(occurrencePayload::add)
                ?: run { blockers += "완료·건너뛰기 또는 날짜 기록이 유효하지 않습니다." }
        }
    }
    val manifest = if (blockers.isEmpty()) LocalMigrationManifest(
        householdId, accountUid, definitionPayload, occurrencePayload,
        names.map { requireNotNull(mapping[it]) },
        requireNotNull(mapping[source.household.defaultMember]),
        rows.occurrences.count { it.workloadPoints == 0 }
    ) else null
    return LocalMigrationPlan(
        rows.definitions.size, rows.occurrences.size, rows.occurrences.count { it.completed },
        rows.occurrences.count { it.skippedAtEpochMillis != null },
        names, blockers.toList(), manifest
    )
}
