package com.hebi.homecrew.data.cloud

import android.content.Context
import android.util.AtomicFile
import com.hebi.homecrew.data.local.HomeCrewDatabase
import com.hebi.homecrew.data.local.HomeCrewLocalBackup
import com.hebi.homecrew.ui.setup.HouseholdSetup
import com.hebi.homecrew.ui.setup.LocalHouseholdStore
import com.hebi.homecrew.ui.setup.validateHouseholdSetup
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal const val ATOMIC_MIGRATION_MAX_ROWS = 300
internal const val ATOMIC_MIGRATION_MAX_BYTES = 512 * 1024
private const val JOURNAL_MAX_BYTES = 40 * 1024 * 1024

internal data class PendingLocalMigration(
    val digest: String,
    val household: HouseholdSetup,
    val mapping: Map<String, String>,
    val backupJson: String,
    val definitions: Int,
    val occurrences: Int,
    val completed: Int,
    val skipped: Int,
    val confirmed: Boolean = false
) {
    init {
        require(digest.matches(Regex("[a-f0-9]{64}")))
        require(validateHouseholdSetup(household))
        require(mapping.size in 1..8 && mapping.values.distinct().size == mapping.size)
        require(definitions in 1..STAGED_MAX_DEFINITIONS &&
            occurrences in 0..STAGED_MAX_OCCURRENCES)
        require(completed in 0..occurrences && skipped in 0..occurrences &&
            completed + skipped <= occurrences)
    }

    fun matches(receipt: RemoteMigrationReceipt): Boolean =
        digest == receipt.digest && definitions == receipt.definitions &&
            occurrences == receipt.occurrences && completed == receipt.completed && skipped == receipt.skipped
}

internal fun atomicMigrationFits(manifest: LocalMigrationManifest): Boolean =
    manifest.definitions.size + manifest.occurrences.size <= ATOMIC_MIGRATION_MAX_ROWS &&
        JSONObject(manifest.payload()).toString().toByteArray(Charsets.UTF_8).size <= ATOMIC_MIGRATION_MAX_BYTES

internal suspend fun readLocalMigrationSource(context: Context): LocalMigrationSource = withContext(Dispatchers.IO) {
    val store = LocalHouseholdStore(context.applicationContext)
    val setup = checkNotNull(store.load())
    val rows = HomeCrewDatabase.get(context.applicationContext).chores().readRows()
    check(setup == store.load()) { "Local setup changed" }
    LocalMigrationSource(setup, rows)
}

internal object LocalMigrationJournalCodec {
    fun encode(value: PendingLocalMigration): ByteArray {
        val setup = value.household
        val result = JSONObject()
            .put("version", 1)
            .put("digest", value.digest)
            .put("homeName", setup.homeName)
            .put("members", JSONArray(setup.members))
            .put("defaultMember", setup.defaultMember)
            .put("templates", JSONArray(setup.selectedTemplateIds))
            .put("mapping", JSONObject(value.mapping))
            .put("backupJson", value.backupJson)
            .put("definitions", value.definitions)
            .put("occurrences", value.occurrences)
            .put("completed", value.completed)
            .put("skipped", value.skipped)
            .put("confirmed", value.confirmed)
        return result.toString().toByteArray(Charsets.UTF_8).also {
            require(it.size in 1..JOURNAL_MAX_BYTES)
        }
    }

    fun decode(bytes: ByteArray): PendingLocalMigration {
        require(bytes.size in 1..JOURNAL_MAX_BYTES)
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        require(json.getInt("version") == 1)
        fun strings(key: String): List<String> = json.getJSONArray(key).let { array ->
            (0 until array.length()).map { array.getString(it) }
        }
        val mapping = json.getJSONObject("mapping")
        val setup = HouseholdSetup(json.getString("homeName"), strings("members"),
            json.getString("defaultMember"), strings("templates"))
        val value = PendingLocalMigration(json.getString("digest"), setup,
            mapping.keys().asSequence().associateWith { mapping.getString(it) },
            json.getString("backupJson"), json.getInt("definitions"), json.getInt("occurrences"),
            json.getInt("completed"), json.getInt("skipped"), json.getBoolean("confirmed"))
        val rows = HomeCrewLocalBackup.decode(value.backupJson, setup)
        require(rows.definitions.size == value.definitions && rows.occurrences.size == value.occurrences &&
            rows.occurrences.count { it.completed } == value.completed &&
            rows.occurrences.count { it.skippedAtEpochMillis != null } == value.skipped)
        return value
    }
}

/** App-private, excluded from device backup; no token or password is stored. */
internal class HomeCrewLocalMigrationJournal(
    context: Context, projectId: String, accountUid: String, householdId: String
) {
    private val file = AtomicFile(File(context.applicationContext.noBackupFilesDir,
        "migration-" + completionJournalKey(projectId, accountUid, householdId) + ".json"))

    private fun read(): PendingLocalMigration? {
        val bytes = try {
            file.openRead().use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= JOURNAL_MAX_BYTES) { "Oversized journal" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } catch (error: FileNotFoundException) {
            if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists() ||
                File(file.baseFile.path + ".new").exists()) throw error
            return null
        }
        return LocalMigrationJournalCodec.decode(bytes)
    }

    private fun write(value: PendingLocalMigration) {
        val bytes = LocalMigrationJournalCodec.encode(value)
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            stream.fd.sync()
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
        check(read() == value) { "Migration backup verification failed" }
    }

    suspend fun load(): PendingLocalMigration? = withContext(Dispatchers.IO) {
        synchronized(lock) { read() }
    }

    suspend fun stage(value: PendingLocalMigration) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            check(!value.confirmed)
            val existing = read()
            check(existing == null || existing == value) { "Another migration is already preserved" }
            write(value)
        }
    }

    suspend fun confirm(receipt: RemoteMigrationReceipt): PendingLocalMigration = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val saved = checkNotNull(read())
            check(saved.matches(receipt)) { "Migration receipt mismatch" }
            saved.copy(confirmed = true).also(::write)
        }
    }

    companion object { private val lock = Any() }
}
