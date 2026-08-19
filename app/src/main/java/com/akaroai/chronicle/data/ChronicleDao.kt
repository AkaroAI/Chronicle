package com.akaroai.chronicle.data

import androidx.room.*
import com.akaroai.chronicle.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChronicleDao {
    @Query("SELECT * FROM campaigns WHERE archived = 0 ORDER BY updatedAt DESC")
    fun campaigns(): Flow<List<CampaignEntity>>

    @Query("SELECT * FROM campaigns WHERE archived = 1 ORDER BY updatedAt DESC")
    fun archivedCampaigns(): Flow<List<CampaignEntity>>

    @Query("SELECT * FROM campaigns WHERE id = :id LIMIT 1")
    suspend fun campaignById(id: Long): CampaignEntity?

    @Insert
    suspend fun insertCampaign(campaign: CampaignEntity): Long

    @Update
    suspend fun updateCampaign(campaign: CampaignEntity)

    @Delete
    suspend fun deleteCampaign(campaign: CampaignEntity)

    @Query("SELECT * FROM messages WHERE campaignId = :campaignId ORDER BY createdAt ASC, id ASC")
    fun messages(campaignId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE campaignId = :campaignId ORDER BY createdAt ASC, id ASC")
    suspend fun messagesSnapshot(campaignId: Long): List<MessageEntity>

    @Insert
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("SELECT * FROM memories WHERE campaignId = :campaignId ORDER BY pinned DESC, createdAt DESC")
    fun memories(campaignId: Long): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE campaignId = :campaignId ORDER BY createdAt ASC, id ASC")
    suspend fun memoriesSnapshot(campaignId: Long): List<MemoryEntity>

    @Insert
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Delete
    suspend fun deleteMemory(memory: MemoryEntity)

    @Query("SELECT * FROM characters WHERE campaignId = :campaignId ORDER BY name COLLATE NOCASE ASC")
    fun characters(campaignId: Long): Flow<List<CharacterEntity>>

    @Query("SELECT * FROM characters WHERE campaignId = :campaignId ORDER BY id ASC")
    suspend fun charactersSnapshot(campaignId: Long): List<CharacterEntity>

    @Query("SELECT * FROM characters WHERE id = :id LIMIT 1")
    suspend fun characterById(id: Long): CharacterEntity?

    @Insert
    suspend fun insertCharacter(character: CharacterEntity): Long

    @Update
    suspend fun updateCharacter(character: CharacterEntity)

    @Delete
    suspend fun deleteCharacter(character: CharacterEntity)

    @Query(
        "SELECT * FROM change_proposals " +
            "WHERE campaignId = :campaignId AND status = 'Pending' " +
            "ORDER BY CASE priority " +
            "WHEN 'Critical' THEN 0 WHEN 'High' THEN 1 WHEN 'Normal' THEN 2 ELSE 3 END, createdAt DESC"
    )
    fun pendingProposals(campaignId: Long): Flow<List<ChangeProposalEntity>>

    @Query("SELECT * FROM change_proposals WHERE campaignId = :campaignId ORDER BY createdAt ASC, id ASC")
    suspend fun proposalsSnapshot(campaignId: Long): List<ChangeProposalEntity>

    @Query(
        "SELECT * FROM change_proposals " +
            "WHERE campaignId = :campaignId AND status = 'Pending' " +
            "AND targetType = :targetType " +
            "AND ((targetId IS NULL AND :targetId IS NULL) OR targetId = :targetId)"
    )
    suspend fun pendingForTarget(
        campaignId: Long,
        targetType: String,
        targetId: Long?
    ): List<ChangeProposalEntity>

    @Insert
    suspend fun insertProposal(proposal: ChangeProposalEntity): Long

    @Update
    suspend fun updateProposal(proposal: ChangeProposalEntity)


    @Query("SELECT * FROM locations WHERE campaignId = :campaignId ORDER BY region COLLATE NOCASE, name COLLATE NOCASE")
    fun locations(campaignId: Long): Flow<List<LocationEntity>>
    @Query("SELECT * FROM locations WHERE campaignId = :campaignId ORDER BY id ASC")
    suspend fun locationsSnapshot(campaignId: Long): List<LocationEntity>
    @Insert suspend fun insertLocation(item: LocationEntity): Long
    @Update suspend fun updateLocation(item: LocationEntity)
    @Delete suspend fun deleteLocation(item: LocationEntity)

    @Query("SELECT * FROM factions WHERE campaignId = :campaignId ORDER BY name COLLATE NOCASE")
    fun factions(campaignId: Long): Flow<List<FactionEntity>>
    @Query("SELECT * FROM factions WHERE campaignId = :campaignId ORDER BY id ASC")
    suspend fun factionsSnapshot(campaignId: Long): List<FactionEntity>
    @Insert suspend fun insertFaction(item: FactionEntity): Long
    @Update suspend fun updateFaction(item: FactionEntity)
    @Delete suspend fun deleteFaction(item: FactionEntity)

    @Query("SELECT * FROM quests WHERE campaignId = :campaignId ORDER BY CASE status WHEN 'Active' THEN 0 WHEN 'Paused' THEN 1 WHEN 'Completed' THEN 2 ELSE 3 END, updatedAt DESC")
    fun quests(campaignId: Long): Flow<List<QuestEntity>>
    @Query("SELECT * FROM quests WHERE campaignId = :campaignId ORDER BY id ASC")
    suspend fun questsSnapshot(campaignId: Long): List<QuestEntity>
    @Insert suspend fun insertQuest(item: QuestEntity): Long
    @Update suspend fun updateQuest(item: QuestEntity)
    @Delete suspend fun deleteQuest(item: QuestEntity)

    @Query("SELECT * FROM timeline_events WHERE campaignId = :campaignId ORDER BY storyOrder DESC, createdAt DESC, id DESC")
    fun timelineEvents(campaignId: Long): Flow<List<TimelineEventEntity>>
    @Query("SELECT * FROM timeline_events WHERE campaignId = :campaignId ORDER BY storyOrder ASC, createdAt ASC, id ASC")
    suspend fun timelineSnapshot(campaignId: Long): List<TimelineEventEntity>
    @Insert suspend fun insertTimelineEvent(item: TimelineEventEntity): Long
    @Delete suspend fun deleteTimelineEvent(item: TimelineEventEntity)

    @Query("UPDATE campaigns SET updatedAt = :whenUpdated WHERE id = :campaignId")
    suspend fun touchCampaign(
        campaignId: Long,
        whenUpdated: Long = System.currentTimeMillis()
    )
}
