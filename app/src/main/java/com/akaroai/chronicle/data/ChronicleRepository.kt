package com.akaroai.chronicle.data

import com.akaroai.chronicle.model.*
import kotlinx.coroutines.flow.Flow

class ChronicleRepository(private val dao: ChronicleDao) {
    fun campaigns(): Flow<List<CampaignEntity>> = dao.campaigns()
    fun messages(campaignId: Long): Flow<List<MessageEntity>> = dao.messages(campaignId)
    fun memories(campaignId: Long): Flow<List<MemoryEntity>> = dao.memories(campaignId)
    fun characters(campaignId: Long): Flow<List<CharacterEntity>> = dao.characters(campaignId)

    suspend fun createCampaign(name: String, description: String = ""): Long =
        dao.insertCampaign(CampaignEntity(name = name.trim(), description = description.trim()))

    suspend fun deleteCampaign(campaign: CampaignEntity) = dao.deleteCampaign(campaign)

    suspend fun addMessage(campaignId: Long, role: String, content: String) {
        dao.insertMessage(MessageEntity(campaignId = campaignId, role = role, content = content.trim()))
        dao.touchCampaign(campaignId)
    }

    suspend fun addMemory(campaignId: Long, title: String, content: String, category: String = "Canon") {
        dao.insertMemory(
            MemoryEntity(
                campaignId = campaignId,
                title = title.trim(),
                content = content.trim(),
                category = category.trim().ifBlank { "Canon" }
            )
        )
        dao.touchCampaign(campaignId)
    }

    suspend fun deleteMemory(memory: MemoryEntity) = dao.deleteMemory(memory)

    suspend fun addCharacter(campaignId: Long, name: String, summary: String, relationship: String) {
        dao.insertCharacter(
            CharacterEntity(
                campaignId = campaignId,
                name = name.trim(),
                summary = summary.trim(),
                relationship = relationship.trim()
            )
        )
        dao.touchCampaign(campaignId)
    }

    suspend fun deleteCharacter(character: CharacterEntity) = dao.deleteCharacter(character)
}
