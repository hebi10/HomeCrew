package com.hebi.homecrew.data.cloud

import java.util.UUID

internal const val STAGED_MAX_DEFINITIONS = 2000
internal const val STAGED_MAX_OCCURRENCES = 100000
internal const val STAGED_MAX_CHUNKS = 2048
private const val CLIENT_CHUNK_BYTES = 100 * 1024
private const val CLIENT_TOTAL_BYTES = 15 * 1024 * 1024
private const val CLIENT_CHUNK_ROWS = 100

internal data class MigrationUploadChunk(
    val index: Int,
    val definitions: List<Map<String, Any?>>,
    val occurrences: List<Map<String, Any?>>
) {
    fun payload(): Map<String, Any?> = mapOf(
        "index" to index, "definitions" to definitions, "occurrences" to occurrences
    )
    val digest: String get() = migrationDigest(payload())
}

internal data class StagedMigrationUpload(
    val manifest: LocalMigrationManifest,
    val chunks: List<MigrationUploadChunk>
) {
    val digest: String get() = manifest.digest
    fun header(): Map<String, Any?> = mapOf(
        "version" to 1, "householdId" to manifest.householdId, "ownerUid" to manifest.ownerUid,
        "digest" to digest, "definitionCount" to manifest.definitions.size,
        "occurrenceCount" to manifest.occurrences.size, "memberUids" to manifest.mappedMemberUids,
        "defaultMemberUid" to manifest.defaultMemberUid, "legacyWorkloadCount" to manifest.legacyWorkloadCount,
        "chunkCount" to chunks.size
    )
}

// Conservative JSON size accounting. Control characters and slash/quote
// escaping are overestimated so server-side bounds are never deliberately bypassed.
private fun jsonUpperBytes(value: Any?): Long = when (value) {
    null -> 4L
    is Boolean -> if (value) 4L else 5L
    is Int, is Long -> value.toString().length.toLong()
    is String -> {
        var total = 2L
        var index = 0
        while (index < value.length) {
            val ch = value[index]
            if (Character.isHighSurrogate(ch)) {
                require(index + 1 < value.length && Character.isLowSurrogate(value[index + 1]))
                total += 12L
                index++
            } else {
                require(!Character.isLowSurrogate(ch))
                total += when {
                    ch.code < 32 -> 6L
                    ch == '"' || ch == '\\' || ch == '/' -> 2L
                    ch.code < 128 -> 1L
                    else -> 6L
                }
            }
            index++
        }
        total
    }
    is List<*> -> 2L + value.sumOf { jsonUpperBytes(it) + 1L }
    is Map<*, *> -> 2L + value.entries.sumOf { jsonUpperBytes(it.key as String) + 1L + jsonUpperBytes(it.value) + 1L }
    else -> error("Unsupported migration payload")
}

internal fun planStagedMigrationUpload(manifest: LocalMigrationManifest): StagedMigrationUpload {
    require(manifest.definitions.size in 1..STAGED_MAX_DEFINITIONS)
    require(manifest.occurrences.size <= STAGED_MAX_OCCURRENCES)
    val chunks = mutableListOf<MigrationUploadChunk>()
    var bytes = 0L
    fun addRows(rows: List<Map<String, Any?>>, isDefinition: Boolean) {
        var pending = mutableListOf<Map<String, Any?>>()
        var pendingBytes = 100L
        fun flush() {
            if (pending.isEmpty()) return
            val chunk = MigrationUploadChunk(chunks.size, if (isDefinition) pending.toList() else emptyList(),
                if (isDefinition) emptyList() else pending.toList())
            val size = jsonUpperBytes(chunk.payload())
            require(size <= CLIENT_CHUNK_BYTES)
            bytes += size
            require(bytes <= CLIENT_TOTAL_BYTES && chunks.size < STAGED_MAX_CHUNKS) {
                "Staged migration exceeds the client safety limit"
            }
            chunks.add(chunk)
            pending = mutableListOf()
            pendingBytes = 100L
        }
        for (row in rows) {
            val size = jsonUpperBytes(row) + 1L
            require(size + 100L <= CLIENT_CHUNK_BYTES)
            if (pending.size >= CLIENT_CHUNK_ROWS || pendingBytes + size > CLIENT_CHUNK_BYTES) flush()
            pending.add(row)
            pendingBytes += size
        }
        flush()
    }
    addRows(manifest.definitions, true)
    addRows(manifest.occurrences, false)
    require(chunks.isNotEmpty())
    require(jsonUpperBytes(manifest.payload()) <= CLIENT_TOTAL_BYTES)
    return StagedMigrationUpload(manifest, chunks)
}

internal data class RemoteMigrationStage(
    val sessionId: String,
    val digest: String,
    val state: String,
    val nextIndex: Int,
    val chunkCount: Int,
    val receivedDefinitions: Int,
    val definitionCount: Int,
    val receivedOccurrences: Int,
    val occurrenceCount: Int,
    val publishIndex: Int,
    val publishedDefinitions: Int,
    val publishedOccurrences: Int
) {
    init {
        require(UUID.fromString(sessionId).toString() == sessionId)
        require(digest.matches(Regex("[a-f0-9]{64}")))
        require(state in setOf("UPLOADING", "VERIFYING", "SEALED", "INVALID", "CANCELLING",
            "PUBLISHING", "ROLLING_BACK", "ROLLED_BACK", "PUBLISHED", "CLEANING"))
        require(chunkCount in 1..STAGED_MAX_CHUNKS && nextIndex in 0..chunkCount && publishIndex in 0..chunkCount)
        require(definitionCount in 1..STAGED_MAX_DEFINITIONS && receivedDefinitions in 0..definitionCount &&
            publishedDefinitions in 0..definitionCount)
        require(occurrenceCount in 0..STAGED_MAX_OCCURRENCES && receivedOccurrences in 0..occurrenceCount &&
            publishedOccurrences in 0..occurrenceCount)
    }
}

internal fun remainingUploadChunks(upload: StagedMigrationUpload, stage: RemoteMigrationStage): List<MigrationUploadChunk> {
    require(stage.digest == upload.digest && stage.chunkCount == upload.chunks.size &&
        stage.definitionCount == upload.manifest.definitions.size &&
        stage.occurrenceCount == upload.manifest.occurrences.size)
    require(stage.state == "UPLOADING" || stage.state == "VERIFYING" || stage.state == "SEALED")
    val prefix = upload.chunks.take(stage.nextIndex)
    require(prefix.sumOf { it.definitions.size } == stage.receivedDefinitions &&
        prefix.sumOf { it.occurrences.size } == stage.receivedOccurrences)
    return upload.chunks.drop(stage.nextIndex)
}
