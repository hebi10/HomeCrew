package com.hebi.homecrew.data.local

import android.content.Context
import com.hebi.homecrew.ui.setup.HouseholdSetup
import com.hebi.homecrew.ui.setup.LocalHouseholdStore
import com.hebi.homecrew.ui.setup.memberNameError
import com.hebi.homecrew.ui.setup.validateHouseholdSetup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal const val LOCAL_MEMBER_CHANGE_KEY = "homecrew.local_member_change.v1"
internal val homeCrewLocalWriteMutex = Mutex()

internal class LocalMemberChangeRejected(val userMessage: String) : IllegalArgumentException(userMessage)

internal data class LocalMemberChangePlan(
    val before: HouseholdSetup,
    val after: HouseholdSetup,
    val member: String,
    val replacement: String?
)

internal fun localMemberReferences(rows: HomeCrewRows): Set<String> =
    (rows.definitions.map { it.defaultOwner } + rows.members.map { it.memberName } +
        rows.occurrences.map { it.assignedMember } + rows.occurrences.mapNotNull { it.completedBy }).toSet()

internal fun planLocalMemberChange(
    setup: HouseholdSetup, rows: HomeCrewRows, member: String, replacement: String?
): LocalMemberChangePlan {
    require(validateHouseholdSetup(setup))
    val index = setup.members.indexOf(member)
    if (index < 0) throw LocalMemberChangeRejected("구성원 정보가 변경되었습니다. 다시 확인해 주세요.")
    val references = localMemberReferences(rows)
    val next = if (replacement == null) {
        if (setup.members.size <= 1) throw LocalMemberChangeRejected("최소 한 명의 구성원이 필요합니다.")
        if (member in references) throw LocalMemberChangeRejected("집안일 배정 또는 과거 기록에 연결된 구성원은 삭제할 수 없습니다. 기록 보호를 위해 이름을 수정할 수 있습니다.")
        val remaining = setup.members.filterNot { it == member }
        setup.copy(members = remaining, defaultMember = if (setup.defaultMember == member) remaining.first() else setup.defaultMember)
    } else {
        val name = replacement.trim()
        memberNameError(name, setup.members, index)?.let { throw LocalMemberChangeRejected(it) }
        if (name.any { it.isISOControl() }) throw LocalMemberChangeRejected("올바른 구성원 이름을 입력해 주세요.")
        if (references.any { it != member && it.equals(name, ignoreCase = true) }) {
            throw LocalMemberChangeRejected("기존 배정 또는 완료 기록의 다른 이름과 중복됩니다.")
        }
        setup.copy(members = setup.members.map { if (it == member) name else it },
            defaultMember = if (setup.defaultMember == member) name else setup.defaultMember)
    }
    require(validateHouseholdSetup(next))
    return LocalMemberChangePlan(setup, next, member, replacement?.trim())
}

internal object LocalMemberChangeJournalCodec {
    private fun setupJson(value: HouseholdSetup): JSONObject = JSONObject()
        .put("homeName", value.homeName).put("members", JSONArray(value.members))
        .put("defaultMember", value.defaultMember).put("templates", JSONArray(value.selectedTemplateIds))

    private fun readSetup(value: JSONObject): HouseholdSetup {
        fun strings(key: String): List<String> = value.getJSONArray(key).let { array ->
            (0 until array.length()).map { array.getString(it) }
        }
        return HouseholdSetup(value.getString("homeName"), strings("members"),
            value.getString("defaultMember"), strings("templates")).also { require(validateHouseholdSetup(it)) }
    }

    fun encode(plan: LocalMemberChangePlan): String = JSONObject()
        .put("version", 1).put("before", setupJson(plan.before)).put("after", setupJson(plan.after))
        .put("member", plan.member).put("replacement", plan.replacement ?: JSONObject.NULL).toString()

    fun decode(value: String): LocalMemberChangePlan {
        val json = JSONObject(value)
        require(json.getInt("version") == 1)
        val before = readSetup(json.getJSONObject("before"))
        val after = readSetup(json.getJSONObject("after"))
        val member = json.getString("member")
        val replacement = if (json.isNull("replacement")) null else json.getString("replacement")
        // Validate the settings transition itself; Room already committed the
        // actual reference rewrite before this journal could become visible.
        val expected = planLocalMemberChange(before, HomeCrewRows(emptyList(), emptyList(), emptyList()), member, replacement)
        require(expected.after == after)
        return expected
    }
}

/**
 * Room commits references and recovery journal together. The legacy setup store
 * is updated only afterwards. A failure leaves the journal intact; startup
 * finishes that exact transition before exposing the normal UI.
 */
internal class HomeCrewLocalMemberCoordinator(
    private val dao: HomeCrewDao,
    private val readSetup: () -> HouseholdSetup?,
    private val saveSetup: (HouseholdSetup) -> Boolean
) {
    private suspend fun recoverLocked(): HouseholdSetup? {
        val encoded = dao.readState(LOCAL_MEMBER_CHANGE_KEY) ?: return readSetup()
        val journal = LocalMemberChangeJournalCodec.decode(encoded)
        val current = readSetup()
        check(current == journal.before || current == journal.after) { "Local setup differs from pending member change" }
        // Recommit even if SharedPreferences memory already contains the new
        // value; a failed disk commit may have changed memory only.
        check(saveSetup(journal.after) && readSetup() == journal.after) { "Member setup persistence failed" }
        check(dao.clearLocalMemberJournal(encoded) == 1) { "Member change journal changed" }
        return journal.after
    }

    suspend fun recoverAndLoad(): HouseholdSetup? = withContext(Dispatchers.IO) {
        homeCrewLocalWriteMutex.withLock { recoverLocked() }
    }

    suspend fun change(expected: HouseholdSetup, member: String, replacement: String?): HouseholdSetup =
        withContext(Dispatchers.IO) {
            homeCrewLocalWriteMutex.withLock {
                val current = recoverLocked()
                if (current != expected) throw LocalMemberChangeRejected("구성원 설정이 달라졌습니다. 다시 확인해 주세요.")
                val plan = dao.applyLocalMemberChange(expected, member, replacement)
                checkNotNull(recoverLocked()).also { check(it == plan.after) }
            }
        }

    companion object {
        fun create(context: Context): HomeCrewLocalMemberCoordinator {
            val app = context.applicationContext
            val store = LocalHouseholdStore(app)
            return HomeCrewLocalMemberCoordinator(HomeCrewDatabase.get(app).chores(), store::load, store::save)
        }
    }
}

internal suspend fun <T> guardedLocalWrite(dao: HomeCrewDao, action: suspend () -> T): T =
    homeCrewLocalWriteMutex.withLock {
        check(dao.readState(LOCAL_MEMBER_CHANGE_KEY) == null) { "Pending local member recovery" }
        action()
    }
