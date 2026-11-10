package com.hebi.homecrew.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChoreDefinitionEntity::class,
        ChoreAssignmentMemberEntity::class,
        ChoreOccurrenceEntity::class,
        AppStateEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class HomeCrewDatabase : RoomDatabase() {
    abstract fun chores(): HomeCrewDao

    companion object {
        @Volatile
        private var instance: HomeCrewDatabase? = null

        fun get(context: Context): HomeCrewDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HomeCrewDatabase::class.java,
                    "homecrew.db"
                ).build().also { instance = it }
            }
    }
}
