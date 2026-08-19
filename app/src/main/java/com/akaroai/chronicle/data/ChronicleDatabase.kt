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
        ChangeProposalEntity::class
    ],
    version = 4,
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

        fun get(context: Context): ChronicleDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ChronicleDatabase::class.java,
                    "chronicle.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
