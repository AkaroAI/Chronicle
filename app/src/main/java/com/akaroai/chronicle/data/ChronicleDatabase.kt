package com.akaroai.chronicle.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.akaroai.chronicle.model.*

@Database(entities=[CampaignEntity::class,MessageEntity::class,MemoryEntity::class,CharacterEntity::class,ChangeProposalEntity::class],version=2,exportSchema=false)
abstract class ChronicleDatabase:RoomDatabase(){
    abstract fun dao():ChronicleDao
    companion object{
        @Volatile private var INSTANCE:ChronicleDatabase?=null
        private val MIGRATION_1_2=object:Migration(1,2){ override fun migrate(db:SupportSQLiteDatabase){
            db.execSQL("ALTER TABLE campaigns ADD COLUMN setting TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE campaigns ADD COLUMN genreTone TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE campaigns ADD COLUMN currentLocation TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE campaigns ADD COLUMN currentObjective TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE campaigns ADD COLUMN playerCharacterId INTEGER")
            db.execSQL("ALTER TABLE campaigns ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
            for(c in listOf("aliases","species","age","pronouns","appearance","personality","backstory","abilities","equipment","affiliations","goals","fears","secrets","injuries","notes")) db.execSQL("ALTER TABLE characters ADD COLUMN $c TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE characters ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE TABLE IF NOT EXISTS change_proposals (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,campaignId INTEGER NOT NULL,summary TEXT NOT NULL,targetType TEXT NOT NULL,targetId INTEGER,proposedChanges TEXT NOT NULL,reason TEXT NOT NULL DEFAULT '',status TEXT NOT NULL DEFAULT 'Pending',createdAt INTEGER NOT NULL,FOREIGN KEY(campaignId) REFERENCES campaigns(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_change_proposals_campaignId ON change_proposals(campaignId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_change_proposals_status ON change_proposals(status)")
        }}
        fun get(context:Context):ChronicleDatabase=INSTANCE?:synchronized(this){ INSTANCE?:Room.databaseBuilder(context.applicationContext,ChronicleDatabase::class.java,"chronicle.db").addMigrations(MIGRATION_1_2).build().also{INSTANCE=it} }
    }
}
