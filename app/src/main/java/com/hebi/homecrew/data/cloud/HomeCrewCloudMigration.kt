package com.hebi.homecrew.data.cloud

import com.hebi.homecrew.data.local.HomeCrewRows

/**
 * Dry-run only. Names from local Room must be mapped explicitly to verified Firebase UIDs.
 * No network request, source DB change or background migration is performed here.
 */
internal data class HomeCrewCloudMigrationPreview(
    val definitions: Int,
    val occurrences: Int,
    val unmappedNames: List<String>,
    val mappingOneToOne: Boolean
) {
    val canProceed: Boolean get() = unmappedNames.isEmpty() && mappingOneToOne
}

internal fun previewCloudMigration(
    rows: HomeCrewRows,
    firebaseUidByLocalName: Map<String, String>
): HomeCrewCloudMigrationPreview {
    val names = (
        rows.members.map { it.memberName } +
            rows.occurrences.map { it.assignedMember } +
            rows.occurrences.mapNotNull { it.completedBy }
        ).filter(String::isNotBlank).distinct().sorted()
    val missing = names.filter { firebaseUidByLocalName[it].isNullOrBlank() }
    val assignedUids = names.mapNotNull { firebaseUidByLocalName[it] }
    val unique = assignedUids.size == assignedUids.distinct().size &&
        assignedUids.all { it.length <= 128 && '/' !in it }
    return HomeCrewCloudMigrationPreview(
        definitions = rows.definitions.size,
        occurrences = rows.occurrences.size,
        unmappedNames = missing,
        mappingOneToOne = unique
    )
}
