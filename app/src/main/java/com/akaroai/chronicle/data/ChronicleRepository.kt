package com.akaroai.chronicle.data

import com.akaroai.chronicle.model.*
import org.json.JSONObject
import kotlinx.coroutines.flow.Flow

class ChronicleRepository(private val dao: ChronicleDao) {
    fun campaigns(): Flow<List<CampaignEntity>> = dao.campaigns()
    fun archivedCampaigns(): Flow<List<CampaignEntity>> = dao.archivedCampaigns()
    fun messages(id: Long) = dao.messages(id)
    fun memories(id: Long) = dao.memories(id)
    fun characters(id: Long) = dao.characters(id)
    fun pendingProposals(id: Long) = dao.pendingProposals(id)

    suspend fun createCampaign(name: String, description: String = "") =
        dao.insertCampaign(CampaignEntity(name = name.trim(), description = description.trim()))

    suspend fun updateCampaign(c: CampaignEntity) =
        dao.updateCampaign(c.copy(updatedAt = System.currentTimeMillis()))

    suspend fun archiveCampaign(c: CampaignEntity) = updateCampaign(c.copy(archived = true))
    suspend fun restoreCampaign(c: CampaignEntity) = updateCampaign(c.copy(archived = false))
    suspend fun deleteCampaign(c: CampaignEntity) = dao.deleteCampaign(c)

    suspend fun addMessage(id: Long, role: String, content: String) {
        dao.insertMessage(MessageEntity(campaignId = id, role = role, content = content.trim()))
        dao.touchCampaign(id)
    }

    suspend fun addMemory(
        id: Long,
        title: String,
        content: String,
        category: String = "Canon"
    ) {
        dao.insertMemory(
            MemoryEntity(
                campaignId = id,
                title = title.trim(),
                content = content.trim(),
                category = category.trim().ifBlank { "Canon" }
            )
        )
        dao.touchCampaign(id)
    }

    suspend fun deleteMemory(m: MemoryEntity) = dao.deleteMemory(m)

    suspend fun addCharacter(c: CharacterEntity) {
        dao.insertCharacter(c.copy(updatedAt = System.currentTimeMillis()))
        dao.touchCampaign(c.campaignId)
    }

    suspend fun updateCharacter(c: CharacterEntity) {
        dao.updateCharacter(c.copy(updatedAt = System.currentTimeMillis()))
        dao.touchCampaign(c.campaignId)
    }

    suspend fun deleteCharacter(c: CharacterEntity) = dao.deleteCharacter(c)

    suspend fun addProposal(proposal: ChangeProposalEntity) {
        dao.insertProposal(proposal)
    }

    suspend fun rejectProposal(proposal: ChangeProposalEntity) {
        dao.updateProposal(proposal.copy(status = "Rejected"))
    }

    suspend fun updateProposalChanges(proposal: ChangeProposalEntity, changes: String) {
        dao.updateProposal(proposal.copy(proposedChanges = changes))
    }

    suspend fun approveProposal(proposal: ChangeProposalEntity, editedChanges: String? = null) {
        val changesRaw = editedChanges ?: proposal.proposedChanges
        val changes = JSONObject(changesRaw)

        when (proposal.targetType) {
            "memory_new" -> {
                val title = changes.optString("title").trim()
                val content = changes.optString("content").trim()
                val category = changes.optString("category", "Canon").trim().ifBlank { "Canon" }
                if (title.isBlank() || content.isBlank()) {
                    error("Memory proposal needs a title and content.")
                }
                addMemory(proposal.campaignId, title, content, category)
            }

            "character_update" -> {
                val id = proposal.targetId ?: error("Character proposal has no character ID.")
                val current = dao.characterById(id) ?: error("Character no longer exists.")
                val fields = changes.optJSONObject("fields") ?: changes

                val updated = current.copy(
                    name = fields.valueOr("name", current.name),
                    aliases = fields.valueOr("aliases", current.aliases),
                    species = fields.valueOr("species", current.species),
                    age = fields.valueOr("age", current.age),
                    pronouns = fields.valueOr("pronouns", current.pronouns),
                    appearance = fields.valueOr("appearance", current.appearance),
                    personality = fields.valueOr("personality", current.personality),
                    backstory = fields.valueOr("backstory", current.backstory),
                    abilities = fields.valueOr("abilities", current.abilities),
                    equipment = fields.valueOr("equipment", current.equipment),
                    relationship = fields.valueOr("relationship", current.relationship),
                    affiliations = fields.valueOr("affiliations", current.affiliations),
                    goals = fields.valueOr("goals", current.goals),
                    fears = fields.valueOr("fears", current.fears),
                    secrets = fields.valueOr("secrets", current.secrets),
                    injuries = fields.valueOr("injuries", current.injuries),
                    notes = fields.valueOr("notes", current.notes),
                    status = fields.valueOr("status", current.status),
                    updatedAt = System.currentTimeMillis()
                )
                updateCharacter(updated)
            }

            "campaign_update" -> {
                val current = dao.campaignById(proposal.campaignId)
                    ?: error("Campaign no longer exists.")
                val fields = changes.optJSONObject("fields") ?: changes
                updateCampaign(
                    current.copy(
                        name = fields.valueOr("name", current.name),
                        description = fields.valueOr("description", current.description),
                        setting = fields.valueOr("setting", current.setting),
                        genreTone = fields.valueOr("genreTone", current.genreTone),
                        currentLocation = fields.valueOr("currentLocation", current.currentLocation),
                        currentObjective = fields.valueOr("currentObjective", current.currentObjective)
                    )
                )
            }

            else -> error("Unsupported proposal type: ${proposal.targetType}")
        }

        dao.updateProposal(
            proposal.copy(
                proposedChanges = changesRaw,
                status = "Approved"
            )
        )
    }

    private fun JSONObject.valueOr(key: String, fallback: String): String {
        if (!has(key) || isNull(key)) return fallback
        return optString(key, fallback)
    }
}
