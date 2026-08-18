package com.akaroai.chronicle.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.akaroai.chronicle.model.*

@Database(
    entities = [
        CampaignEntity::class,
        MessageEntity::class,
        MemoryEntity::class,
        CharacterEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ChronicleDatabase : RoomDatabase() {
    abstract fun dao(): ChronicleDao

    companion object {
        @Volatile private var INSTANCE: ChronicleDatabase? = null

        fun get(context: Context): ChronicleDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ChronicleDatabase::class.java,
                    "chronicle.db"
                ).build().also { INSTANCE = it }
            }
    }
}
