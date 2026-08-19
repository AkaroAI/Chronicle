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
    fun locations(id: Long) = dao.locations(id)
    fun factions(id: Long) = dao.factions(id)
    fun quests(id: Long) = dao.quests(id)
    fun timelineEvents(id: Long) = dao.timelineEvents(id)


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
                proposals = dao.proposalsSnapshot(campaignId),
                locations = dao.locationsSnapshot(campaignId),
                factions = dao.factionsSnapshot(campaignId),
                quests = dao.questsSnapshot(campaignId),
                timelineEvents = dao.timelineSnapshot(campaignId)
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

        backup.locations.forEach { dao.insertLocation(it.copy(id = 0, campaignId = importedId)) }
        backup.factions.forEach { dao.insertFaction(it.copy(id = 0, campaignId = importedId)) }
        backup.quests.forEach { dao.insertQuest(it.copy(id = 0, campaignId = importedId)) }
        backup.timelineEvents.forEach { dao.insertTimelineEvent(it.copy(id = 0, campaignId = importedId)) }

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

    suspend fun buildCanonicalContextSnapshot(campaign: CampaignEntity): String {
        val characters = dao.charactersSnapshot(campaign.id)
        val memories = dao.memoriesSnapshot(campaign.id)
        val locations = dao.locationsSnapshot(campaign.id)
        val factions = dao.factionsSnapshot(campaign.id)
        val quests = dao.questsSnapshot(campaign.id)
        val timeline = dao.timelineSnapshot(campaign.id)

        return buildString {
            appendLine("CAMPAIGN:")
            appendLine("Name: ${campaign.name}")
            appendLine("Description: ${campaign.description}")
            appendLine("Setting: ${campaign.setting}")
            appendLine("Genre/Tone: ${campaign.genreTone}")
            appendLine("Current location: ${campaign.currentLocation}")
            appendLine("Current objective: ${campaign.currentObjective}")
            appendLine()

            appendLine("CANONICAL CHARACTER RECORDS — DATABASE SOURCE OF TRUTH:")
            characters.forEach { c ->
                appendLine(
                    "${c.name} [ID ${c.id}] [CAST ${c.castTier}] [INTEGRITY ${c.integrityMode}] | " +
                        "aliases=${c.aliases} | species=${c.species} | age=${c.age} | pronouns=${c.pronouns} | " +
                        "appearance=${c.appearance} | personality=${c.personality} | backstory=${c.backstory} | " +
                        "abilities=${c.abilities} | equipment=${c.equipment} | relationship=${c.relationship} | " +
                        "affiliations=${c.affiliations} | goals=${c.goals} | fears=${c.fears} | secrets=${c.secrets} | " +
                        "injuries=${c.injuries} | notes=${c.notes} | status=${c.status}"
                )
            }
            appendLine()

            appendLine("WORLD STATE:")
            locations.forEach { l ->
                appendLine(
                    "LOCATION | ${l.name} | region=${l.region} | parent=${l.parentLocation} | " +
                        "discovery=${l.discoveryState} | status=${l.status} | ${l.description} | notes=${l.notes}"
                )
            }
            factions.forEach { f ->
                appendLine(
                    "FACTION | ${f.name} | relationship=${f.relationshipToParty} | status=${f.status} | " +
                        "alignment=${f.alignment} | goals=${f.goals} | ${f.description} | notes=${f.notes}"
                )
            }
            quests.forEach { q ->
                appendLine(
                    "QUEST | EXACT TITLE=${q.title} | status=${q.status} | importance=${q.importance} | " +
                        "objective=${q.objective} | location=${q.relatedLocation} | faction=${q.relatedFaction} | " +
                        "summary=${q.summary} | notes=${q.notes}"
                )
            }
            appendLine()

            appendLine("TIMELINE:")
            timeline.takeLast(40).forEach { e ->
                appendLine(
                    "TIMELINE | order=${e.storyOrder} | ${e.title} | type=${e.eventType} | " +
                        "importance=${e.importance} | location=${e.location} | characters=${e.involvedCharacters} | " +
                        "arc=${e.storyArc} | ${e.summary}"
                )
            }
            appendLine()

            if (memories.isNotEmpty()) {
                appendLine("MEMORIES:")
                memories.takeLast(80).forEach { m ->
                    appendLine("[${m.category}] ${m.title}: ${m.content}")
                }
            }
        }
    }

    suspend fun addTimelineEvent(event: TimelineEventEntity) {
        val current = dao.timelineSnapshot(event.campaignId)
        val order = if (event.storyOrder > 0) event.storyOrder else ((current.maxOfOrNull { it.storyOrder } ?: 0L) + 1L)
        dao.insertTimelineEvent(event.copy(storyOrder = order))
        dao.touchCampaign(event.campaignId)
    }

    suspend fun deleteTimelineEvent(event: TimelineEventEntity) = dao.deleteTimelineEvent(event)

    private suspend fun upsertLocation(campaignId: Long, changes: JSONObject) {
        val name = changes.optString("name").trim()
        if (name.isBlank()) error("Location needs a name.")
        val old = dao.locationsSnapshot(campaignId).firstOrNull { it.name.equals(name, true) }
        val next = (old ?: LocationEntity(campaignId = campaignId, name = name)).copy(
            region = changes.optString("region", old?.region ?: ""),
            parentLocation = changes.optString("parentLocation", old?.parentLocation ?: ""),
            description = changes.optString("description", old?.description ?: ""),
            discoveryState = changes.optString("discoveryState", old?.discoveryState ?: "Discovered"),
            status = changes.optString("status", old?.status ?: "Active"),
            notes = changes.optString("notes", old?.notes ?: ""),
            updatedAt = System.currentTimeMillis()
        )
        if (old == null) dao.insertLocation(next) else dao.updateLocation(next)
    }

    private suspend fun upsertFaction(campaignId: Long, changes: JSONObject) {
        val name = changes.optString("name").trim()
        if (name.isBlank()) error("Faction needs a name.")
        val old = dao.factionsSnapshot(campaignId).firstOrNull { it.name.equals(name, true) }
        val next = (old ?: FactionEntity(campaignId = campaignId, name = name)).copy(
            description = changes.optString("description", old?.description ?: ""),
            alignment = changes.optString("alignment", old?.alignment ?: ""),
            relationshipToParty = changes.optString("relationshipToParty", old?.relationshipToParty ?: "Unknown"),
            status = changes.optString("status", old?.status ?: "Active"),
            goals = changes.optString("goals", old?.goals ?: ""),
            notes = changes.optString("notes", old?.notes ?: ""),
            updatedAt = System.currentTimeMillis()
        )
        if (old == null) dao.insertFaction(next) else dao.updateFaction(next)
    }

    private suspend fun upsertQuest(campaignId: Long, changes: JSONObject) {
        val title = changes.optString("title").trim()
        if (title.isBlank()) error("Quest needs a title.")
        val old = dao.questsSnapshot(campaignId).firstOrNull { it.title.equals(title, true) }
        val next = (old ?: QuestEntity(campaignId = campaignId, title = title)).copy(
            summary = changes.optString("summary", old?.summary ?: ""),
            status = changes.optString("status", old?.status ?: "Active"),
            objective = changes.optString("objective", old?.objective ?: ""),
            relatedLocation = changes.optString("relatedLocation", old?.relatedLocation ?: ""),
            relatedFaction = changes.optString("relatedFaction", old?.relatedFaction ?: ""),
            importance = changes.optString("importance", old?.importance ?: "Normal"),
            notes = changes.optString("notes", old?.notes ?: ""),
            updatedAt = System.currentTimeMillis()
        )
        if (old == null) dao.insertQuest(next) else dao.updateQuest(next)
    }

    suspend fun addProposal(proposal: ChangeProposalEntity) {
        val worldTypes = setOf("location_upsert", "faction_upsert", "quest_upsert", "timeline_event_new")
        val oldPending = if (proposal.targetType in worldTypes) {
            val identity = proposalIdentity(proposal)
            dao.proposalsSnapshot(proposal.campaignId).filter {
                it.status == "Pending" &&
                    it.targetType == proposal.targetType &&
                    proposalIdentity(it) == identity
            }
        } else {
            dao.pendingForTarget(
                proposal.campaignId,
                proposal.targetType,
                proposal.targetId
            )
        }
        val newFields = ProposalParser.changedFieldNames(proposal.proposedChanges)

        // Do not generate Review noise for world facts already represented identically in canon.
        if (proposal.targetType in worldTypes && isWorldNoOp(proposal)) {
            return
        }

        // Exact duplicate pending proposals are noise; ignore them.
        if (oldPending.any { normalizeJson(it.proposedChanges) == normalizeJson(proposal.proposedChanges) }) {
            return
        }

        var guarded = proposal

        if (proposal.targetType == "character_new") {
            val changes = JSONObject(proposal.proposedChanges)
            val proposedName = changes.optString("name").trim()
            if (proposedName.isBlank()) return

            val existingNames = dao.charactersSnapshot(proposal.campaignId)
                .map { it.name.trim().lowercase() }
                .toSet()
            if (proposedName.lowercase() in existingNames) return

            val duplicatePending = dao.proposalsSnapshot(proposal.campaignId)
                .filter { it.status == "Pending" && it.targetType == "character_new" }
                .any {
                    runCatching {
                        JSONObject(it.proposedChanges).optString("name").trim()
                            .equals(proposedName, ignoreCase = true)
                    }.getOrDefault(false)
                }
            if (duplicatePending) return

            if (proposal.evidenceType == "Assistant Only" || proposal.evidenceType == "Unverified") {
                guarded = proposal.copy(
                    integrityWarning = "New character record came from ${proposal.evidenceType.lowercase()} evidence. Review the sheet and evidence before creating canon."
                )
            }
        }

        if (proposal.targetType == "character_update" && proposal.targetId != null) {
            val character = dao.characterById(proposal.targetId)
            if (character != null) {
                guarded = proposal.copy(
                    integrityWarning = IntegrityPolicy.warning(
                        character,
                        newFields,
                        proposal.changeMode
                    )
                )
            }
        }

        val newId = dao.insertProposal(guarded)

        if (proposal.targetType in worldTypes) {
            oldPending.forEach { old ->
                dao.updateProposal(old.copy(status = "Superseded", supersededById = newId))
            }
        } else if (newFields.isNotEmpty()) {
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

        val overrideLocked = changes.optBoolean("__integrityOverride", false)
        val requestedMode = changes.optString("__changeMode", proposal.changeMode)
            .takeIf { it in setOf("Append", "Replace", "Clear") } ?: "Replace"
        val evidence = changes.optString("__evidenceType", proposal.evidenceType)
            .takeIf { it in setOf("Player Confirmed", "Story Event", "Assistant Only", "Unverified") }
            ?: "Unverified"

        changes.remove("__integrityOverride")
        changes.remove("__changeMode")
        changes.remove("__evidenceType")

        when (proposal.targetType) {
            "character_new" -> {
                val name = changes.optString("name").trim()
                if (name.isBlank()) error("New character proposal needs a name.")

                val existing = dao.charactersSnapshot(proposal.campaignId)
                    .firstOrNull { it.name.trim().equals(name, ignoreCase = true) }
                if (existing != null) {
                    error("$name already exists as a canonical character. Use a character update instead.")
                }

                if (evidence == "Assistant Only" || evidence == "Unverified") {
                    error("New character records require Player Confirmed or Story Event evidence before becoming canon.")
                }

                val tier = changes.optString("castTier", "Supporting")
                    .takeIf { it in setOf("Main", "Secondary", "Supporting", "Background") }
                    ?: "Supporting"

                addCharacter(
                    CharacterEntity(
                        campaignId = proposal.campaignId,
                        name = name,
                        aliases = changes.optString("aliases"),
                        species = changes.optString("species"),
                        age = changes.optString("age"),
                        pronouns = changes.optString("pronouns"),
                        appearance = changes.optString("appearance"),
                        personality = changes.optString("personality"),
                        backstory = changes.optString("backstory"),
                        abilities = changes.optString("abilities"),
                        equipment = changes.optString("equipment"),
                        relationship = changes.optString("relationship"),
                        affiliations = changes.optString("affiliations"),
                        goals = changes.optString("goals"),
                        fears = changes.optString("fears"),
                        secrets = changes.optString("secrets"),
                        injuries = changes.optString("injuries"),
                        notes = changes.optString("notes"),
                        status = changes.optString("status", "Active").ifBlank { "Active" },
                        castTier = tier,
                        integrityMode = if (tier == "Main") "Strict" else "Balanced"
                    )
                )
            }

            "memory_new" -> {
                val title = changes.optString("title").trim()
                val content = changes.optString("content").trim()
                val category = changes.optString("category", "Canon").trim().ifBlank { "Canon" }
                if (title.isBlank() || content.isBlank()) error("Memory proposal needs a title and content.")
                if (evidence == "Assistant Only") {
                    error("Assistant-only narration cannot be promoted to canon memory without changing its evidence classification.")
                }
                addMemory(proposal.campaignId, title, content, category)
            }

            "character_update" -> {
                val id = proposal.targetId ?: error("Character proposal has no character ID.")
                val current = dao.characterById(id) ?: error("Character no longer exists.")
                val fields = changes.optJSONObject("fields") ?: changes
                val changed = jsonKeys(fields)
                val protected = IntegrityPolicy.protectedOverlap(current, changed)

                if (protected.isNotEmpty() && !overrideLocked) {
                    error("Protected fields require explicit override: ${protected.joinToString(", ")}")
                }
                if (evidence == "Assistant Only" && changed.any {
                        it in setOf("name","aliases","species","age","pronouns","personality",
                            "backstory","abilities","relationship","affiliations","goals","fears","secrets")
                    }) {
                    error("Assistant-only narration cannot rewrite character identity. Reclassify the evidence only if the story actually established it.")
                }

                fun next(key: String, old: String): String {
                    if (!fields.has(key) || fields.isNull(key)) return old
                    return IntegrityPolicy.applyMode(old, fields.optString(key), requestedMode)
                }

                updateCharacter(
                    current.copy(
                        name = next("name", current.name),
                        aliases = next("aliases", current.aliases),
                        species = next("species", current.species),
                        age = next("age", current.age),
                        pronouns = next("pronouns", current.pronouns),
                        appearance = next("appearance", current.appearance),
                        personality = next("personality", current.personality),
                        backstory = next("backstory", current.backstory),
                        abilities = next("abilities", current.abilities),
                        equipment = next("equipment", current.equipment),
                        relationship = next("relationship", current.relationship),
                        affiliations = next("affiliations", current.affiliations),
                        goals = next("goals", current.goals),
                        fears = next("fears", current.fears),
                        secrets = next("secrets", current.secrets),
                        injuries = next("injuries", current.injuries),
                        notes = next("notes", current.notes),
                        status = next("status", current.status)
                    )
                )
            }

            "cast_tier_update" -> {
                val id = proposal.targetId ?: error("Cast-tier proposal has no character ID.")
                val current = dao.characterById(id) ?: error("Character no longer exists.")
                val tier = changes.optString("castTier").trim()
                if (tier !in setOf("Main", "Secondary", "Supporting", "Background")) {
                    error("Invalid cast tier.")
                }
                updateCharacter(current.copy(castTier = tier))
            }

            "location_upsert" -> {
                if (evidence == "Assistant Only" || evidence == "Unverified") {
                    error("World-state changes need Player Confirmed or Story Event evidence before becoming canon.")
                }
                upsertLocation(proposal.campaignId, changes)
            }

            "faction_upsert" -> {
                if (evidence == "Assistant Only" || evidence == "Unverified") {
                    error("Faction changes need Player Confirmed or Story Event evidence before becoming canon.")
                }
                upsertFaction(proposal.campaignId, changes)
            }

            "quest_upsert" -> {
                if (evidence == "Assistant Only" || evidence == "Unverified") {
                    error("Quest changes need Player Confirmed or Story Event evidence before becoming canon.")
                }
                upsertQuest(proposal.campaignId, changes)
            }

            "timeline_event_new" -> {
                if (evidence == "Assistant Only" || evidence == "Unverified") {
                    error("Timeline events need Player Confirmed or Story Event evidence before becoming canon.")
                }
                val title = changes.optString("title").trim()
                val summary = changes.optString("summary").trim()
                if (title.isBlank() || summary.isBlank()) error("Timeline event needs a title and summary.")
                addTimelineEvent(
                    TimelineEventEntity(
                        campaignId = proposal.campaignId,
                        title = title,
                        summary = summary,
                        eventType = changes.optString("eventType", "Event"),
                        location = changes.optString("location"),
                        involvedCharacters = changes.optString("involvedCharacters"),
                        storyArc = changes.optString("storyArc"),
                        importance = changes.optString("importance", proposal.priority),
                        source = evidence
                    )
                )
            }

            "campaign_update" -> {
                if (evidence == "Assistant Only") {
                    error("Assistant-only narration cannot rewrite campaign state without review classification.")
                }
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
                proposedChanges = changes.toString(),
                changeMode = requestedMode,
                evidenceType = evidence,
                integrityWarning = "",
                status = "Approved"
            )
        )
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
                    castTier = c.castTier,
                    integrityMode = if (c.castTier == "Main") "Strict" else "Balanced"
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

        draft.locations.forEach { l ->
            dao.insertLocation(
                LocationEntity(
                    campaignId = campaignId,
                    name = l.name,
                    region = l.region,
                    parentLocation = l.parentLocation,
                    description = l.description,
                    discoveryState = l.discoveryState,
                    status = l.status,
                    notes = l.notes
                )
            )
        }

        draft.factions.forEach { f ->
            dao.insertFaction(
                FactionEntity(
                    campaignId = campaignId,
                    name = f.name,
                    description = f.description,
                    alignment = f.alignment,
                    relationshipToParty = f.relationshipToParty,
                    status = f.status,
                    goals = f.goals,
                    notes = f.notes
                )
            )
        }

        draft.quests.forEach { q ->
            dao.insertQuest(
                QuestEntity(
                    campaignId = campaignId,
                    title = q.title,
                    summary = q.summary,
                    status = q.status,
                    objective = q.objective,
                    relatedLocation = q.relatedLocation,
                    relatedFaction = q.relatedFaction,
                    importance = q.importance,
                    notes = q.notes
                )
            )
        }

        draft.timelineEvents.forEachIndexed { index, e ->
            dao.insertTimelineEvent(
                TimelineEventEntity(
                    campaignId = campaignId,
                    title = e.title,
                    summary = e.summary,
                    eventType = e.eventType,
                    location = e.location,
                    involvedCharacters = e.involvedCharacters,
                    storyArc = e.storyArc,
                    importance = e.importance,
                    source = "Imported Campaign",
                    storyOrder = index.toLong() + 1L
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

    private suspend fun isWorldNoOp(p: ChangeProposalEntity): Boolean {
        return runCatching {
            val o = JSONObject(p.proposedChanges)
            when (p.targetType) {
                "location_upsert" -> {
                    val name = o.optString("name").trim()
                    val old = dao.locationsSnapshot(p.campaignId)
                        .firstOrNull { it.name.trim().equals(name, ignoreCase = true) }
                        ?: return@runCatching false
                    fun same(key: String, current: String) =
                        !o.has(key) || o.isNull(key) || o.optString(key) == current
                    same("region", old.region) &&
                        same("parentLocation", old.parentLocation) &&
                        same("description", old.description) &&
                        same("discoveryState", old.discoveryState) &&
                        same("status", old.status) &&
                        same("notes", old.notes)
                }

                "faction_upsert" -> {
                    val name = o.optString("name").trim()
                    val old = dao.factionsSnapshot(p.campaignId)
                        .firstOrNull { it.name.trim().equals(name, ignoreCase = true) }
                        ?: return@runCatching false
                    fun same(key: String, current: String) =
                        !o.has(key) || o.isNull(key) || o.optString(key) == current
                    same("description", old.description) &&
                        same("alignment", old.alignment) &&
                        same("relationshipToParty", old.relationshipToParty) &&
                        same("status", old.status) &&
                        same("goals", old.goals) &&
                        same("notes", old.notes)
                }

                "quest_upsert" -> {
                    val title = o.optString("title").trim()
                    val old = dao.questsSnapshot(p.campaignId)
                        .firstOrNull { it.title.trim().equals(title, ignoreCase = true) }
                        ?: return@runCatching false
                    fun same(key: String, current: String) =
                        !o.has(key) || o.isNull(key) || o.optString(key) == current
                    same("summary", old.summary) &&
                        same("status", old.status) &&
                        same("objective", old.objective) &&
                        same("relatedLocation", old.relatedLocation) &&
                        same("relatedFaction", old.relatedFaction) &&
                        same("importance", old.importance) &&
                        same("notes", old.notes)
                }

                "timeline_event_new" -> {
                    val title = o.optString("title").trim()
                    val summary = o.optString("summary").trim()
                    dao.timelineSnapshot(p.campaignId).any {
                        it.title.trim().equals(title, ignoreCase = true) &&
                            it.summary.trim().equals(summary, ignoreCase = true)
                    }
                }

                else -> false
            }
        }.getOrDefault(false)
    }

    private fun proposalIdentity(p: ChangeProposalEntity): String {
        return runCatching {
            val o = JSONObject(p.proposedChanges)
            when (p.targetType) {
                "location_upsert", "faction_upsert" -> o.optString("name").trim().lowercase()
                "quest_upsert" -> o.optString("title").trim().lowercase()
                "timeline_event_new" -> {
                    val title = o.optString("title").trim().lowercase()
                    val summary = o.optString("summary").trim().lowercase()
                    "$title|$summary"
                }
                else -> "${p.targetType}|${p.targetId ?: 0}"
            }
        }.getOrDefault("${p.targetType}|${p.targetId ?: 0}")
    }

    private fun normalizeJson(raw: String): String =
        runCatching { JSONObject(raw).toString() }.getOrDefault(raw.trim())

    private fun JSONObject.valueOr(key: String, fallback: String): String {
        if (!has(key) || isNull(key)) return fallback
        return optString(key, fallback)
    }

    private fun jsonKeys(o: JSONObject): Set<String> = buildSet {
        val keys = o.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!key.startsWith("__")) add(key)
        }
    }
}
