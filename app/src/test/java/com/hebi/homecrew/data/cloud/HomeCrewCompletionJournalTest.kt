package com.hebi.homecrew.data.cloud

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCrewCompletionJournalTest {
    private class MemoryStorage : CompletionJournalStorage {
        val values = mutableMapOf<String, String>()
        var failWrites = false
        override fun read(key: String): String? = values[key]
        override fun write(key: String, value: String?): Boolean {
            if (failWrites) return false
            if (value == null) values.remove(key) else values[key] = value
            return true
        }
    }

    private val command = RemoteCompletionCommand("occurrence", true, 4, "00000000-0000-4000-8000-000000000001")
    private val next = command.copy(completed = false, expectedRevision = 5,
        requestId = "00000000-0000-4000-8000-000000000002")

    @Test fun restartRestoresIdenticalCommandAndBlocksOverwritingIt() = runBlocking {
        val storage = MemoryStorage()
        val first = HomeCrewCompletionJournal(storage, "demo-project", "uid-a", "house")
        first.stage(command)
        val restarted = HomeCrewCompletionJournal(storage, "demo-project", "uid-a", "house")
        assertEquals(command, restarted.load())
        restarted.stage(command)
        assertTrue(runCatching { restarted.stage(next) }.isFailure)
        assertTrue(runCatching { restarted.remove(next) }.isFailure)
        assertEquals(command, restarted.load())
        restarted.remove(command)
        assertNull(first.load())
        restarted.stage(next)
        assertEquals(next, first.load())
    }

    @Test fun otherAccountsHousesAndProjectsCannotSeeQueuedCommand() = runBlocking {
        val storage = MemoryStorage()
        val own = HomeCrewCompletionJournal(storage, "demo-project", "uid-a", "house")
        own.stage(command)
        assertNull(HomeCrewCompletionJournal(storage, "demo-project", "uid-b", "house").load())
        assertNull(HomeCrewCompletionJournal(storage, "demo-project", "uid-a", "other-house").load())
        assertNull(HomeCrewCompletionJournal(storage, "other-project", "uid-a", "house").load())
        assertFalse(storage.values.keys.single().contains("uid-a"))
        assertEquals(command, own.load())
    }

    @Test fun failedDiskCommitAndCorruptRecordsFailClosed() = runBlocking {
        val storage = MemoryStorage()
        val journal = HomeCrewCompletionJournal(storage, "demo-project", "uid-a", "house")
        storage.failWrites = true
        assertTrue(runCatching { journal.stage(command) }.isFailure)
        assertNull(journal.load())
        storage.failWrites = false
        journal.stage(command)
        storage.failWrites = true
        assertTrue(runCatching { journal.remove(command) }.isFailure)
        assertEquals(command, journal.load())
        storage.values[completionJournalKey("demo-project", "uid-a", "house")] = "invalid"
        assertTrue(runCatching { journal.load() }.isFailure)
        assertTrue(runCatching { journal.stage(next) }.isFailure)
    }
}
