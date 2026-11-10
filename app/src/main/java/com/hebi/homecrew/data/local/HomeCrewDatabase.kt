package com.hebi.homecrew.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(
    entities = [
        ChoreDefinitionEntity::class,
        ChoreAssignmentMemberEntity::class,
        ChoreOccurrenceEntity::class,
        AppStateEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class HomeCrewDatabase : RoomDatabase() {
    abstract fun chores(): HomeCrewDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE chore_occurrences " +
                        "ADD COLUMN workloadPoints INTEGER NOT NULL DEFAULT 0"
                )
                connection.execSQL(
                    "UPDATE chore_occurrences SET workloadPoints = " +
                        "COALESCE((SELECT points FROM chore_definitions " +
                        "WHERE chore_definitions.id = chore_occurrences.definitionId), 0)"
                )
            }
        }

        @Volatile
        private var instance: HomeCrewDatabase? = null

        fun get(context: Context): HomeCrewDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HomeCrewDatabase::class.java,
                    "homecrew.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
