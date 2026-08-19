package com.akaroai.chronicle.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.akaroai.chronicle.model.*

@Database(
    entities = [
        CampaignEntity::class,
        MessageEntity::class,
        MemoryEntity::class,
        CharacterEntity::class,
        ChangeProposalEntity::class,
        LocationEntity::class,
        FactionEntity::class,
        QuestEntity::class,
        TimelineEventEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class ChronicleDatabase : RoomDatabase() {
    abstract fun dao(): ChronicleDao

    companion object {
        @Volatile private var INSTANCE: ChronicleDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE campaigns ADD COLUMN setting TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE campaigns ADD COLUMN genreTone TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE campaigns ADD COLUMN currentLocation TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE campaigns ADD COLUMN currentObjective TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE campaigns ADD COLUMN playerCharacterId INTEGER")
                db.execSQL("ALTER TABLE campaigns ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")

                for (c in listOf(
                    "aliases","species","age","pronouns","appearance","personality",
                    "backstory","abilities","equipment","affiliations","goals","fears",
                    "secrets","injuries","notes"
                )) {
                    db.execSQL("ALTER TABLE characters ADD COLUMN $c TEXT NOT NULL DEFAULT ''")
                }
                db.execSQL("ALTER TABLE characters ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS change_proposals (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                        "campaignId INTEGER NOT NULL," +
                        "summary TEXT NOT NULL," +
                        "targetType TEXT NOT NULL," +
                        "targetId INTEGER," +
                        "proposedChanges TEXT NOT NULL," +
                        "reason TEXT NOT NULL DEFAULT ''," +
                        "status TEXT NOT NULL DEFAULT 'Pending'," +
                        "createdAt INTEGER NOT NULL," +
                        "FOREIGN KEY(campaignId) REFERENCES campaigns(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_change_proposals_campaignId ON change_proposals(campaignId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_change_proposals_status ON change_proposals(status)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE characters ADD COLUMN castTier TEXT NOT NULL DEFAULT 'Supporting'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_characters_castTier ON characters(castTier)")

                db.execSQL("ALTER TABLE change_proposals ADD COLUMN priority TEXT NOT NULL DEFAULT 'Normal'")
                db.execSQL("ALTER TABLE change_proposals ADD COLUMN groupType TEXT NOT NULL DEFAULT 'Other'")
                db.execSQL("ALTER TABLE change_proposals ADD COLUMN groupLabel TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE change_proposals ADD COLUMN supersededById INTEGER")

                db.execSQL("CREATE INDEX IF NOT EXISTS index_change_proposals_priority ON change_proposals(priority)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_change_proposals_groupType ON change_proposals(groupType)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE characters ADD COLUMN integrityMode TEXT NOT NULL DEFAULT 'Balanced'")
                db.execSQL("ALTER TABLE characters ADD COLUMN protectedFields TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE change_proposals ADD COLUMN changeMode TEXT NOT NULL DEFAULT 'Replace'")
                db.execSQL("ALTER TABLE change_proposals ADD COLUMN evidenceType TEXT NOT NULL DEFAULT 'Unverified'")
                db.execSQL("ALTER TABLE change_proposals ADD COLUMN integrityWarning TEXT NOT NULL DEFAULT ''")
            }
        }


        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS locations (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, campaignId INTEGER NOT NULL, name TEXT NOT NULL, region TEXT NOT NULL DEFAULT '', parentLocation TEXT NOT NULL DEFAULT '', description TEXT NOT NULL DEFAULT '', discoveryState TEXT NOT NULL DEFAULT 'Discovered', status TEXT NOT NULL DEFAULT 'Active', notes TEXT NOT NULL DEFAULT '', firstSeenAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, FOREIGN KEY(campaignId) REFERENCES campaigns(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_locations_campaignId ON locations(campaignId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_locations_name ON locations(name)")
                db.execSQL("CREATE TABLE IF NOT EXISTS factions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, campaignId INTEGER NOT NULL, name TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', alignment TEXT NOT NULL DEFAULT '', relationshipToParty TEXT NOT NULL DEFAULT 'Unknown', status TEXT NOT NULL DEFAULT 'Active', goals TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '', createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, FOREIGN KEY(campaignId) REFERENCES campaigns(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_factions_campaignId ON factions(campaignId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_factions_name ON factions(name)")
                db.execSQL("CREATE TABLE IF NOT EXISTS quests (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, campaignId INTEGER NOT NULL, title TEXT NOT NULL, summary TEXT NOT NULL DEFAULT '', status TEXT NOT NULL DEFAULT 'Active', objective TEXT NOT NULL DEFAULT '', relatedLocation TEXT NOT NULL DEFAULT '', relatedFaction TEXT NOT NULL DEFAULT '', importance TEXT NOT NULL DEFAULT 'Normal', notes TEXT NOT NULL DEFAULT '', createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, FOREIGN KEY(campaignId) REFERENCES campaigns(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_quests_campaignId ON quests(campaignId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_quests_status ON quests(status)")
                db.execSQL("CREATE TABLE IF NOT EXISTS timeline_events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, campaignId INTEGER NOT NULL, title TEXT NOT NULL, summary TEXT NOT NULL, eventType TEXT NOT NULL DEFAULT 'Event', location TEXT NOT NULL DEFAULT '', involvedCharacters TEXT NOT NULL DEFAULT '', storyArc TEXT NOT NULL DEFAULT '', importance TEXT NOT NULL DEFAULT 'Normal', source TEXT NOT NULL DEFAULT 'Review', storyOrder INTEGER NOT NULL DEFAULT 0, createdAt INTEGER NOT NULL, FOREIGN KEY(campaignId) REFERENCES campaigns(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_timeline_events_campaignId ON timeline_events(campaignId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_timeline_events_storyOrder ON timeline_events(storyOrder)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_timeline_events_importance ON timeline_events(importance)")
            }
        }

        fun get(context: Context): ChronicleDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ChronicleDatabase::class.java,
                    "chronicle.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
