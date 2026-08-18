package com.akaroai.chronicle.data

import androidx.room.*
import com.akaroai.chronicle.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChronicleDao {
    @Query("SELECT * FROM campaigns WHERE archived = 0 ORDER BY updatedAt DESC") fun campaigns():Flow<List<CampaignEntity>>
    @Query("SELECT * FROM campaigns WHERE archived = 1 ORDER BY updatedAt DESC") fun archivedCampaigns():Flow<List<CampaignEntity>>
    @Insert suspend fun insertCampaign(campaign:CampaignEntity):Long
    @Update suspend fun updateCampaign(campaign:CampaignEntity)
    @Delete suspend fun deleteCampaign(campaign:CampaignEntity)
    @Query("SELECT * FROM messages WHERE campaignId=:campaignId ORDER BY createdAt ASC,id ASC") fun messages(campaignId:Long):Flow<List<MessageEntity>>
    @Insert suspend fun insertMessage(message:MessageEntity):Long
    @Query("SELECT * FROM memories WHERE campaignId=:campaignId ORDER BY pinned DESC,createdAt DESC") fun memories(campaignId:Long):Flow<List<MemoryEntity>>
    @Insert suspend fun insertMemory(memory:MemoryEntity):Long
    @Delete suspend fun deleteMemory(memory:MemoryEntity)
    @Query("SELECT * FROM characters WHERE campaignId=:campaignId ORDER BY name COLLATE NOCASE ASC") fun characters(campaignId:Long):Flow<List<CharacterEntity>>
    @Insert suspend fun insertCharacter(character:CharacterEntity):Long
    @Update suspend fun updateCharacter(character:CharacterEntity)
    @Delete suspend fun deleteCharacter(character:CharacterEntity)
    @Query("SELECT * FROM change_proposals WHERE campaignId=:campaignId AND status='Pending' ORDER BY createdAt DESC") fun pendingProposals(campaignId:Long):Flow<List<ChangeProposalEntity>>
    @Insert suspend fun insertProposal(proposal:ChangeProposalEntity):Long
    @Update suspend fun updateProposal(proposal:ChangeProposalEntity)
    @Query("UPDATE campaigns SET updatedAt=:whenUpdated WHERE id=:campaignId") suspend fun touchCampaign(campaignId:Long,whenUpdated:Long=System.currentTimeMillis())
}
