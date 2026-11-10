package com.hebi.homecrew.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeCrewMigrationTest {
    @Test
    fun legacyVersionOneCompletionKeepsWorkloadOnUpgrade() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val filename = "homecrew-upgrade-v1-to-v2.db"
        context.deleteDatabase(filename)
        val raw = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(filename), null)
        try {
            raw.execSQL(
                "CREATE TABLE chore_definitions (" +
                    "id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, category TEXT NOT NULL, " +
                    "defaultOwner TEXT NOT NULL, repeatLabel TEXT NOT NULL, points INTEGER NOT NULL, " +
                    "scheduledTime TEXT NOT NULL, description TEXT NOT NULL, startEpochDay INTEGER, " +
                    "repeatMode TEXT NOT NULL, weekdayMask INTEGER NOT NULL, " +
                    "repeatIntervalDays INTEGER NOT NULL, assignmentMode TEXT NOT NULL, " +
                    "createdAtEpochMillis INTEGER NOT NULL, updatedAtEpochMillis INTEGER NOT NULL)"
            )
            raw.execSQL(
                "CREATE TABLE chore_assignment_members (" +
                    "definitionId TEXT NOT NULL, position INTEGER NOT NULL, memberName TEXT NOT NULL, " +
                    "PRIMARY KEY(definitionId, position), " +
                    "FOREIGN KEY(definitionId) REFERENCES chore_definitions(id) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            raw.execSQL(
                "CREATE INDEX index_chore_assignment_members_definitionId " +
                    "ON chore_assignment_members(definitionId)"
            )
            raw.execSQL(
                "CREATE TABLE chore_occurrences (" +
                    "id TEXT NOT NULL PRIMARY KEY, definitionId TEXT NOT NULL, " +
                    "scheduledEpochDay INTEGER NOT NULL, assignedMember TEXT NOT NULL, " +
                    "completed INTEGER NOT NULL, completedAtEpochMillis INTEGER, " +
                    "completedBy TEXT, skippedAtEpochMillis INTEGER, skipReason TEXT, " +
                    "FOREIGN KEY(definitionId) REFERENCES chore_definitions(id) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            raw.execSQL(
                "CREATE UNIQUE INDEX index_chore_occurrences_definitionId_scheduledEpochDay " +
                    "ON chore_occurrences(definitionId,scheduledEpochDay)"
            )
            raw.execSQL("CREATE TABLE app_state (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)")
            raw.execSQL(
                "INSERT INTO chore_definitions VALUES " +
                    "('dishes', '설거지', '주방', '나', '매일', 2, '20:00', '', " +
                    "20037, 'DAILY', 0, 2, 'FIXED', 1, 1)"
            )
            raw.execSQL(
                "INSERT INTO chore_occurrences VALUES " +
                    "('dishes-20037', 'dishes', 20037, '나', 1, 12345, '나', NULL, NULL)"
            )
            raw.version = 1
        } finally {
            raw.close()
        }

        val upgraded = Room.databaseBuilder(context, HomeCrewDatabase::class.java, filename)
            .addMigrations(HomeCrewDatabase.MIGRATION_1_2)
            .build()
        try {
            val restored = upgraded.chores().findOccurrence("dishes-20037")
            assertEquals(2, restored?.workloadPoints)
            assertTrue(restored?.completed == true)
            assertEquals("나", restored?.completedBy)
        } finally {
            upgraded.close()
            context.deleteDatabase(filename)
        }
    }
}
