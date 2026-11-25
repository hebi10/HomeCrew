package com.hebi.homecrew.data.cloud

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class HomeCrewStagedMigrationTest {
    private val manifest = LocalMigrationManifest("house", "owner",
        listOf(mapOf("id" to "task", "name" to "설거지")),
        (0..300).map { mapOf("id" to "row-$it", "definitionId" to "task", "date" to it) },
        listOf("owner"), "owner", 0)

    @Test fun deterministicChunksPreserveEveryRowAndResumeFromServerPrefix() {
        val upload = planStagedMigrationUpload(manifest)
        assertEquals(5, upload.chunks.size)
        assertEquals(manifest.definitions, upload.chunks.flatMap { it.definitions })
        assertEquals(manifest.occurrences, upload.chunks.flatMap { it.occurrences })
        assertEquals(upload.chunks.map { it.digest }, planStagedMigrationUpload(manifest).chunks.map { it.digest })
        val stage = RemoteMigrationStage(UUID.randomUUID().toString(), manifest.digest, "UPLOADING",
            2, 5, 1, 1, 100, 301, 0, 0, 0)
        assertEquals(listOf(2,3,4), remainingUploadChunks(upload, stage).map { it.index })
        assertThrows(IllegalArgumentException::class.java) {
            remainingUploadChunks(upload, stage.copy(receivedOccurrences = 99))
        }
        assertThrows(IllegalArgumentException::class.java) {
            remainingUploadChunks(upload, stage.copy(digest = "0".repeat(64)))
        }
    }

    @Test fun chunksAreBoundedWithoutSplittingIndividualRecords() {
        val wide = manifest.copy(occurrences = (0..50).map {
            mapOf("id" to "row-$it", "description" to "한".repeat(3000))
        })
        val upload = planStagedMigrationUpload(wide)
        assertTrue(upload.chunks.size > 2)
        assertTrue(upload.chunks.all { it.definitions.size + it.occurrences.size <= 100 })
        assertEquals(wide.occurrences, upload.chunks.flatMap { it.occurrences })
        assertThrows(IllegalArgumentException::class.java) {
            planStagedMigrationUpload(manifest.copy(occurrences = listOf(mapOf("description" to "한".repeat(100_000)))))
        }
    }
}
