package com.hebi.homecrew.data.local

import com.hebi.homecrew.ui.setup.HouseholdSetup
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Versioned, user-selected local backup. No credentials or device-specific paths are stored.
 * Import validates every row before the caller begins the replacement transaction.
 */
object HomeCrewLocalBackup {
    private const val FORMAT = "homecrew-local"
    private const val VERSION = 1
    const val MAX_BYTES = 16_000_000
    private const val MAX_DEFINITIONS = 2_000
    private const val MAX_MEMBERS = 10_000
    private const val MAX_OCCURRENCES = 100_000

    fun encode(household: HouseholdSetup, rows: HomeCrewRows): String {
        validateRows(rows)
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("homeName", household.homeName)
            .put("householdMembers", JSONArray(household.members))
        val definitions = JSONArray()
        rows.definitions.forEach { item ->
            definitions.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("category", item.category)
                    .put("defaultOwner", item.defaultOwner)
                    .put("repeatLabel", item.repeatLabel)
                    .put("points", item.points)
                    .put("scheduledTime", item.scheduledTime)
                    .put("description", item.description)
                    .put("startEpochDay", item.startEpochDay ?: JSONObject.NULL)
                    .put("repeatMode", item.repeatMode)
                    .put("weekdayMask", item.weekdayMask)
                    .put("repeatIntervalDays", item.repeatIntervalDays)
                    .put("assignmentMode", item.assignmentMode)
                    .put("createdAtEpochMillis", item.createdAtEpochMillis)
                    .put("updatedAtEpochMillis", item.updatedAtEpochMillis)
            )
        }
        val members = JSONArray()
        rows.members.forEach { item ->
            members.put(
                JSONObject()
                    .put("definitionId", item.definitionId)
                    .put("position", item.position)
                    .put("memberName", item.memberName)
            )
        }
        val occurrences = JSONArray()
        rows.occurrences.forEach { item ->
            occurrences.put(
                JSONObject()
                    .put("id", item.id)
                    .put("definitionId", item.definitionId)
                    .put("scheduledEpochDay", item.scheduledEpochDay)
                    .put("assignedMember", item.assignedMember)
                    .put("workloadPoints", item.workloadPoints)
                    .put("completed", item.completed)
                    .put("completedAtEpochMillis", item.completedAtEpochMillis ?: JSONObject.NULL)
                    .put("completedBy", item.completedBy ?: JSONObject.NULL)
                    .put("skippedAtEpochMillis", item.skippedAtEpochMillis ?: JSONObject.NULL)
                    .put("skipReason", item.skipReason ?: JSONObject.NULL)
            )
        }
        val output = root
            .put("definitions", definitions)
            .put("assignmentMembers", members)
            .put("occurrences", occurrences)
            .toString()
        require(output.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) {
            "백업 데이터가 최대 16MB를 초과했습니다."
        }
        return output
    }

    fun decode(content: String, household: HouseholdSetup): HomeCrewRows {
        require(content.toByteArray(Charsets.UTF_8).size in 1..MAX_BYTES) {
            "백업 파일 크기가 허용 범위를 벗어났습니다."
        }
        try {
            val root = JSONObject(content)
            require(root.getString("format") == FORMAT && root.getInt("version") == VERSION) {
                "지원하지 않는 HomeCrew 백업 형식입니다."
            }
            val members = root.getJSONArray("householdMembers")
            val backedUpMembers = (0 until members.length()).map { members.getString(it) }
            require(root.getString("homeName") == household.homeName &&
                backedUpMembers == household.members) {
                "집 이름과 구성원 순서가 백업 당시와 일치해야 복원할 수 있습니다."
            }
            val definitions = root.getJSONArray("definitions")
            val assignments = root.getJSONArray("assignmentMembers")
            val occurrences = root.getJSONArray("occurrences")
            require(definitions.length() <= MAX_DEFINITIONS &&
                assignments.length() <= MAX_MEMBERS &&
                occurrences.length() <= MAX_OCCURRENCES) {
                "백업 데이터 항목 수가 허용 범위를 초과했습니다."
            }
            val result = HomeCrewRows(
                definitions = (0 until definitions.length()).map { i ->
                    definitions.getJSONObject(i).run {
                        ChoreDefinitionEntity(
                            id = getString("id"),
                            name = getString("name"),
                            category = getString("category"),
                            defaultOwner = getString("defaultOwner"),
                            repeatLabel = getString("repeatLabel"),
                            points = getInt("points"),
                            scheduledTime = getString("scheduledTime"),
                            description = getString("description"),
                            startEpochDay = optionalLong("startEpochDay"),
                            repeatMode = getString("repeatMode"),
                            weekdayMask = getInt("weekdayMask"),
                            repeatIntervalDays = getInt("repeatIntervalDays"),
                            assignmentMode = getString("assignmentMode"),
                            createdAtEpochMillis = getLong("createdAtEpochMillis"),
                            updatedAtEpochMillis = getLong("updatedAtEpochMillis")
                        )
                    }
                },
                members = (0 until assignments.length()).map { i ->
                    assignments.getJSONObject(i).run {
                        ChoreAssignmentMemberEntity(
                            definitionId = getString("definitionId"),
                            position = getInt("position"),
                            memberName = getString("memberName")
                        )
                    }
                },
                occurrences = (0 until occurrences.length()).map { i ->
                    occurrences.getJSONObject(i).run {
                        ChoreOccurrenceEntity(
                            id = getString("id"),
                            definitionId = getString("definitionId"),
                            scheduledEpochDay = getLong("scheduledEpochDay"),
                            assignedMember = getString("assignedMember"),
                            workloadPoints = getInt("workloadPoints"),
                            completed = getBoolean("completed"),
                            completedAtEpochMillis = optionalLong("completedAtEpochMillis"),
                            completedBy = optionalString("completedBy"),
                            skippedAtEpochMillis = optionalLong("skippedAtEpochMillis"),
                            skipReason = optionalString("skipReason")
                        )
                    }
                }
            )
            validateRows(result)
            return result
        } catch (error: JSONException) {
            throw IllegalArgumentException("백업 파일이 올바르지 않거나 손상되었습니다.", error)
        }
    }

    private fun JSONObject.optionalString(key: String): String? =
        if (isNull(key)) null else getString(key)

    private fun JSONObject.optionalLong(key: String): Long? =
        if (isNull(key)) null else getLong(key)

    private fun validateRows(rows: HomeCrewRows) {
        require(rows.definitions.size <= MAX_DEFINITIONS &&
            rows.members.size <= MAX_MEMBERS &&
            rows.occurrences.size <= MAX_OCCURRENCES) {
            "백업 항목 수가 허용 범위를 초과했습니다."
        }
        val definitionIds = rows.definitions.map { it.id }
        require(definitionIds.none { it.isBlank() } && definitionIds.distinct().size == definitionIds.size) {
            "중복되거나 비어 있는 집안일 ID가 있습니다."
        }
        val ids = definitionIds.toSet()
        require(rows.definitions.all { it.points in 1..5 &&
            it.name.length in 1..40 && it.description.length <= 300 &&
            it.repeatIntervalDays in 1..30 &&
            it.repeatMode in setOf("NONE", "DAILY", "WEEKLY", "BIWEEKLY", "MONTHLY", "CUSTOM") &&
            it.assignmentMode in setOf("FIXED", "ROUND_ROBIN", "BALANCED") }) {
            "지원하지 않는 집안일 규칙이 포함되어 있습니다."
        }
        require(rows.members.all { it.definitionId in ids && it.position >= 0 &&
            it.memberName.isNotBlank() } &&
            rows.members.map { it.definitionId to it.position }.distinct().size == rows.members.size) {
            "백업의 담당자 순서가 올바르지 않습니다."
        }
        require(rows.definitions.all { definition ->
            rows.members.any { it.definitionId == definition.id }
        }) {
            "담당자 정보가 누락된 집안일이 있습니다."
        }
        require(rows.occurrences.all { item ->
            item.definitionId in ids && item.id.isNotBlank() &&
                item.assignedMember.isNotBlank() && item.workloadPoints in 0..5 &&
                !(item.completed && item.skippedAtEpochMillis != null) &&
                (item.skippedAtEpochMillis != null || item.skipReason == null) &&
                (item.skipReason == null || item.skipReason.length <= 120)
        } && rows.occurrences.map { it.id }.distinct().size == rows.occurrences.size &&
            rows.occurrences.map { it.definitionId to it.scheduledEpochDay }.distinct().size ==
                rows.occurrences.size) {
            "백업의 회차·완료·건너뛰기 기록이 올바르지 않습니다."
        }
    }
}
