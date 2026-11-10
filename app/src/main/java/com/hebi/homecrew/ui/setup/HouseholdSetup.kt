package com.hebi.homecrew.ui.setup

import com.hebi.homecrew.ui.screens.ChoreDefinition
import com.hebi.homecrew.ui.screens.DemoChore
import java.time.LocalDate

data class HouseholdSetup(
    val homeName: String,
    val members: List<String>,
    val defaultMember: String,
    val selectedTemplateIds: List<String>
)

data class ChoreTemplate(
    val id: String,
    val name: String,
    val category: String,
    val repeat: String,
    val points: Int,
    val time: String
)

val homeCrewTemplates: List<ChoreTemplate> = listOf(
    ChoreTemplate("dishes", "설거지", "주방", "매일", 2, "20:00"),
    ChoreTemplate("kitchen", "주방 정리", "주방", "매일", 2, "21:00"),
    ChoreTemplate("living", "거실 청소", "청소", "주 2회", 3, "10:00"),
    ChoreTemplate("bathroom", "욕실 청소", "청소", "주 1회", 4, "19:00"),
    ChoreTemplate("laundry", "빨래 정리", "세탁", "주 2회", 2, "14:00"),
    ChoreTemplate("washing", "세탁기 돌리기", "세탁", "주 2회", 2, "18:00"),
    ChoreTemplate("recycle", "분리수거", "쓰레기", "화·금", 3, "21:00"),
    ChoreTemplate("foodwaste", "음식물 쓰레기", "쓰레기", "매일", 2, "13:00"),
    ChoreTemplate("plants", "식물 물주기", "생활", "주 1회", 1, "09:00"),
    ChoreTemplate("bedding", "침구 정리", "생활", "주 1회", 3, "11:00"),
    ChoreTemplate("petbowl", "반려동물 식기 세척", "반려동물", "매일", 2, "20:00"),
    ChoreTemplate("petwalk", "반려동물 산책", "반려동물", "매일", 3, "18:00")
)

val suggestedTemplateIds = listOf("dishes", "recycle", "bathroom", "laundry")

fun houseNameError(value: String): String? = when {
    value.trim().isEmpty() -> "집 이름을 입력해 주세요."
    value.trim().length > 20 -> "집 이름은 20자 이내로 입력해 주세요."
    else -> null
}

fun memberNameError(
    value: String,
    members: List<String>,
    editingIndex: Int? = null
): String? {
    val name = value.trim()
    return when {
        name.isEmpty() -> "구성원 이름을 입력해 주세요."
        name.length > 12 -> "구성원 이름은 12자 이내로 입력해 주세요."
        members.withIndex().any { (index, member) ->
            index != editingIndex && member.equals(name, ignoreCase = true)
        } -> "같은 이름의 구성원이 이미 있습니다."
        else -> null
    }
}

fun validateHouseholdSetup(setup: HouseholdSetup): Boolean =
    houseNameError(setup.homeName) == null &&
        setup.members.isNotEmpty() &&
        setup.members.all { memberNameError(it, emptyList()) == null } &&
        setup.members.distinctBy { it.lowercase() }.size == setup.members.size &&
        setup.defaultMember in setup.members &&
        setup.selectedTemplateIds.all { id -> homeCrewTemplates.any { it.id == id } } &&
        setup.selectedTemplateIds.distinct().size == setup.selectedTemplateIds.size

fun previewChoresForSetup(
    setup: HouseholdSetup,
    today: LocalDate
): List<DemoChore> {
    require(validateHouseholdSetup(setup))
    return homeCrewTemplates.filter { it.id in setup.selectedTemplateIds }
        .mapIndexed { index, template ->
            val definition = ChoreDefinition(
                id = template.id,
                name = template.name,
                category = template.category,
                owner = setup.members[index % setup.members.size],
                repeat = template.repeat,
                points = template.points,
                time = template.time
            )
            DemoChore(
                id = "${template.id}-${today.toEpochDay()}",
                definition = definition,
                date = today
            )
        }
}
