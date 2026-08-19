package com.akaroai.chronicle.data

import com.akaroai.chronicle.model.*
import com.akaroai.chronicle.provider.ProposalParser
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

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

    suspend fun addMemory(id: Long, title: String, content: String, category: String = "Canon") {
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

    suspend fun exportCampaign(campaignId: Long, output: OutputStream) {
        val campaign = dao.campaignById(campaignId) ?: error("Campaign no longer exists.")
        ChronicleBackup.write(
            ChronicleBackupData(
                campaign = campaign,
                messages = dao.messagesSnapshot(campaignId),
                memories = dao.memoriesSnapshot(campaignId),
                characters = dao.charactersSnapshot(campaignId),
                proposals = dao.proposalsSnapshot(campaignId)
            ),
            output
        )
    }

    suspend fun importCampaign(input: InputStream, replaceCampaign: CampaignEntity? = null): Long {
        val backup = ChronicleBackup.read(input)
        val source = backup.campaign

        val importedId = dao.insertCampaign(
            source.copy(
                id = 0,
                name = if (replaceCampaign == null) "${source.name} (Imported)" else source.name,
                playerCharacterId = null,
                archived = false,
                updatedAt = System.currentTimeMillis()
            )
        )

        val characterIdMap = mutableMapOf<Long, Long>()
        backup.characters.forEach { old ->
            val newId = dao.insertCharacter(
                old.copy(
                    id = 0,
                    campaignId = importedId,
                    updatedAt = System.currentTimeMillis()
                )
            )
            characterIdMap[old.id] = newId
        }

        backup.memories.forEach { old ->
            dao.insertMemory(old.copy(id = 0, campaignId = importedId))
        }

        backup.messages.forEach { old ->
            dao.insertMessage(old.copy(id = 0, campaignId = importedId))
        }

        val proposalIdMap = mutableMapOf<Long, Long>()
        backup.proposals.forEach { old ->
            val mappedTarget = old.targetId?.let { characterIdMap[it] ?: it }
            val newId = dao.insertProposal(
                old.copy(
                    id = 0,
                    campaignId = importedId,
                    targetId = mappedTarget,
                    supersededById = null
                )
            )
            proposalIdMap[old.id] = newId
        }

        val insertedProposals = dao.proposalsSnapshot(importedId).associateBy { it.id }
        backup.proposals.forEach { old ->
            val newId = proposalIdMap[old.id] ?: return@forEach
            val newSuperseded = old.supersededById?.let { proposalIdMap[it] } ?: return@forEach
            insertedProposals[newId]?.let {
                dao.updateProposal(it.copy(supersededById = newSuperseded))
            }
        }

        source.playerCharacterId?.let { oldPlayer ->
            characterIdMap[oldPlayer]?.let { newPlayer ->
                dao.campaignById(importedId)?.let {
                    dao.updateCampaign(it.copy(playerCharacterId = newPlayer))
                }
            }
        }

        if (replaceCampaign != null) dao.deleteCampaign(replaceCampaign)
        return importedId
    }

    suspend fun addProposal(proposal: ChangeProposalEntity) {
        val oldPending = dao.pendingForTarget(
            proposal.campaignId,
            proposal.targetType,
            proposal.targetId
        )
        val newFields = ProposalParser.changedFieldNames(proposal.proposedChanges)
        val newId = dao.insertProposal(proposal)

        if (newFields.isNotEmpty()) {
            oldPending.forEach { old ->
                val overlap = ProposalParser.changedFieldNames(old.proposedChanges).intersect(newFields)
                if (overlap.isNotEmpty()) {
                    dao.updateProposal(old.copy(status = "Superseded", supersededById = newId))
                }
            }
        }
    }

    suspend fun rejectProposal(proposal: ChangeProposalEntity) {
        dao.updateProposal(proposal.copy(status = "Rejected"))
    }

    suspend fun approveProposal(proposal: ChangeProposalEntity, editedChanges: String? = null) {
        val changesRaw = editedChanges ?: proposal.proposedChanges
        val changes = JSONObject(changesRaw)

        when (proposal.targetType) {
            "memory_new" -> {
                val title = changes.optString("title").trim()
                val content = changes.optString("content").trim()
                val category = changes.optString("category", "Canon").trim().ifBlank { "Canon" }
                if (title.isBlank() || content.isBlank()) error("Memory proposal needs a title and content.")
                addMemory(proposal.campaignId, title, content, category)
            }

            "character_update" -> {
                val id = proposal.targetId ?: error("Character proposal has no character ID.")
                val current = dao.characterById(id) ?: error("Character no longer exists.")
                val fields = changes.optJSONObject("fields") ?: changes
                updateCharacter(
                    current.copy(
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
                        status = fields.valueOr("status", current.status)
                    )
                )
            }

            "cast_tier_update" -> {
                val id = proposal.targetId ?: error("Cast-tier proposal has no character ID.")
                val current = dao.characterById(id) ?: error("Character no longer exists.")
                val tier = changes.optString("castTier").trim()
                if (tier !in setOf("Main", "Secondary", "Supporting", "Background")) error("Invalid cast tier.")
                updateCharacter(current.copy(castTier = tier))
            }

            "campaign_update" -> {
                val current = dao.campaignById(proposal.campaignId) ?: error("Campaign no longer exists.")
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

        dao.updateProposal(proposal.copy(proposedChanges = changesRaw, status = "Approved"))
    }

    suspend fun approveAll(proposals: List<ChangeProposalEntity>) {
        proposals.forEach { approveProposal(it) }
    }

    suspend fun rejectAll(proposals: List<ChangeProposalEntity>) {
        proposals.forEach { rejectProposal(it) }
    }


    suspend fun commitExternalImport(draft: ExternalImportDraft): Long {
        val campaignId = dao.insertCampaign(
            CampaignEntity(
                name = draft.campaignName.trim().ifBlank { "Imported Campaign" },
                description = draft.description.trim(),
                setting = draft.setting.trim(),
                genreTone = draft.genreTone.trim(),
                currentLocation = draft.currentLocation.trim(),
                currentObjective = draft.currentObjective.trim()
            )
        )

        draft.characters.forEach { c ->
            dao.insertCharacter(
                CharacterEntity(
                    campaignId = campaignId,
                    name = c.name,
                    species = c.species,
                    age = c.age,
                    pronouns = c.pronouns,
                    appearance = c.appearance,
                    personality = c.personality,
                    backstory = c.backstory,
                    abilities = c.abilities,
                    equipment = c.equipment,
                    relationship = c.relationship,
                    affiliations = c.affiliations,
                    goals = c.goals,
                    fears = c.fears,
                    secrets = c.secrets,
                    injuries = c.injuries,
                    notes = c.notes,
                    status = c.status,
                    castTier = c.castTier
                )
            )
        }

        draft.memories.forEach { m ->
            dao.insertMemory(
                MemoryEntity(
                    campaignId = campaignId,
                    category = m.category,
                    title = m.title,
                    content = m.content,
                    pinned = true
                )
            )
        }

        draft.messages.forEach { m ->
            dao.insertMessage(
                MessageEntity(
                    campaignId = campaignId,
                    role = m.role,
                    content = m.content
                )
            )
        }

        // Keep the source available even when its formatting cannot be converted to chat rows.
        if (draft.messages.isEmpty() && draft.sourceText.isNotBlank()) {
            dao.insertMemory(
                MemoryEntity(
                    campaignId = campaignId,
                    category = "Imported Source",
                    title = "Original imported campaign material",
                    content = draft.sourceText,
                    pinned = false
                )
            )
        }
        dao.touchCampaign(campaignId)
        return campaignId
    }

    private fun JSONObject.valueOr(key: String, fallback: String): String {
        if (!has(key) || isNull(key)) return fallback
        return optString(key, fallback)
    }
}
