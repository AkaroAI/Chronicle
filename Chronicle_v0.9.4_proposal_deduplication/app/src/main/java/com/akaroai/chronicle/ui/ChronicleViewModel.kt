package com.akaroai.chronicle.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.akaroai.chronicle.data.ChronicleRepository
import com.akaroai.chronicle.data.ExternalCampaignImport
import com.akaroai.chronicle.data.ExternalImportDraft
import com.akaroai.chronicle.model.*
import com.akaroai.chronicle.provider.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChronicleViewModel(
    private val repository: ChronicleRepository,
    private val settingsStore: ProviderSettingsStore
) : ViewModel() {

    val campaigns = repository.campaigns()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val archivedCampaigns = repository.archivedCampaigns()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val selectedId = MutableStateFlow<Long?>(null)

    val selectedCampaign = combine(campaigns, selectedId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val messages = selectedCampaign.filterNotNull()
        .flatMapLatest { repository.messages(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memories = selectedCampaign.filterNotNull()
        .flatMapLatest { repository.memories(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val characters = selectedCampaign.filterNotNull()
        .flatMapLatest { repository.characters(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingProposals = selectedCampaign.filterNotNull()
        .flatMapLatest { repository.pendingProposals(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val locations = selectedCampaign.filterNotNull()
        .flatMapLatest { repository.locations(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val factions = selectedCampaign.filterNotNull()
        .flatMapLatest { repository.factions(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val quests = selectedCampaign.filterNotNull()
        .flatMapLatest { repository.quests(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val timelineEvents = selectedCampaign.filterNotNull()
        .flatMapLatest { repository.timelineEvents(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _providerSettings = MutableStateFlow(settingsStore.load())
    val providerSettings = _providerSettings.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()

    private val _isReviewScanning = MutableStateFlow(false)
    val isReviewScanning = _isReviewScanning.asStateFlow()

    private var _queuedManualRescan: Boolean = false

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError = _lastError.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()

    private val _externalImportDraft = MutableStateFlow<ExternalImportDraft?>(null)
    val externalImportDraft = _externalImportDraft.asStateFlow()

    private val _isImportAnalyzing = MutableStateFlow(false)
    val isImportAnalyzing = _isImportAnalyzing.asStateFlow()

    init {
        viewModelScope.launch {
            campaigns.collect { list ->
                if (selectedId.value == null || list.none { it.id == selectedId.value }) {
                    selectedId.value = list.firstOrNull()?.id
                }
            }
        }
    }

    fun clearError() { _lastError.value = null }
    fun clearNotice() { _notice.value = null }

    fun saveProviderSettings(s: ProviderSettings) {
        settingsStore.save(s)
        _providerSettings.value = settingsStore.load()
    }

    fun selectCampaign(id: Long) { selectedId.value = id }

    fun createCampaign(n: String, d: String = "") {
        if (n.isBlank()) return
        viewModelScope.launch { selectedId.value = repository.createCampaign(n, d) }
    }

    fun updateCampaign(c: CampaignEntity) =
        viewModelScope.launch { repository.updateCampaign(c) }

    fun archiveCampaign(c: CampaignEntity) =
        viewModelScope.launch {
            repository.archiveCampaign(c)
            if (selectedId.value == c.id) selectedId.value = null
        }

    fun restoreCampaign(c: CampaignEntity) =
        viewModelScope.launch { repository.restoreCampaign(c) }

    fun deleteCampaign(c: CampaignEntity) =
        viewModelScope.launch {
            repository.deleteCampaign(c)
            if (selectedId.value == c.id) selectedId.value = null
        }

    fun exportSelectedCampaign(context: Context, uri: Uri) {
        val campaign = selectedCampaign.value ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openOutputStream(uri)
                        ?: error("Could not open the selected save location.")
                    stream.use { repository.exportCampaign(campaign.id, it) }
                }
                _notice.value = "${campaign.name} exported successfully."
            } catch (t: Throwable) {
                _lastError.value = t.message ?: "Campaign export failed."
            }
        }
    }

    fun importCampaign(context: Context, uri: Uri, replaceCurrent: Boolean) {
        val current = selectedCampaign.value
        viewModelScope.launch {
            try {
                val importedId = withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: error("Could not open that Chronicle file.")
                    stream.use {
                        repository.importCampaign(
                            it,
                            if (replaceCurrent) current else null
                        )
                    }
                }
                selectedId.value = importedId
                _notice.value = if (replaceCurrent && current != null) {
                    "Campaign restored from backup."
                } else {
                    "Campaign imported as a new copy."
                }
            } catch (t: Throwable) {
                _lastError.value = t.message ?: "Campaign import failed."
            }
        }
    }

    fun analyzeExternalCampaign(context: Context, uri: Uri) {
        if (!_providerSettings.value.enabled || _isImportAnalyzing.value) {
            _lastError.value = "Enable the AI provider before importing an external campaign."
            return
        }
        viewModelScope.launch {
            _isImportAnalyzing.value = true
            _lastError.value = null
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Could not read that campaign file.")
                }
                if (text.isBlank()) error("That campaign file is empty.")
                if (text.length > 1_500_000) error("This file is too large for one-pass import. Split it into smaller parts first.")

                val provider: AiProvider = OpenAiCompatibleProvider { settingsStore.load() }
                val system = """
                    You are Chronicle's external campaign migration analyzer.
                    Convert supplied campaign material into a cautious structured draft.
                    Return ONLY one JSON object. Never invent missing facts.

                    CONFIDENCE:
                    High confidence = explicitly established/repeated.
                    Needs review = plausible but not fully certain.
                    Ambiguous = conflicting, unclear, hypothetical, or identity uncertain.

                    CAST TIERS:
                    Main = central protagonist/core companion.
                    Secondary = recurring important ally/rival/family/villain/mentor.
                    Supporting = recurring arc-relevant character.
                    Background = incidental/minor character.
                    Do not promote a character merely because their name appears often in one scene.

                    Extract durable canon, not every sentence. Preserve contradictions by marking Ambiguous.
                    Do not silently reconcile conflicting source statements.

                    WORLD-STATE EXTRACTION:
                    - Locations: include only named places that matter to continuity. Preserve region/parent hierarchy when explicit.
                    - Discovery state must reflect the source; do not mark Visited unless the party actually went there.
                    - Factions: extract recurring/important organizations and their established relationship to the party.
                    - Quests/story threads: extract active, paused, completed, or failed objectives that matter beyond one line.
                    - Timeline: extract meaningful milestones only (major discoveries, battles, decisions, relationship shifts,
                      quest outcomes, transformations, world changes). Do not turn routine dialogue into timeline entries.
                    - Do not duplicate the same world object under slightly different wording.

                    JSON SCHEMA:
                    {
                      "campaignName":"...",
                      "description":"...",
                      "setting":"...",
                      "genreTone":"...",
                      "currentLocation":"...",
                      "currentObjective":"...",
                      "characters":[{
                        "name":"...","castTier":"Main|Secondary|Supporting|Background",
                        "species":"","age":"","pronouns":"","appearance":"","personality":"",
                        "backstory":"","abilities":"","equipment":"","relationship":"",
                        "affiliations":"","goals":"","fears":"","secrets":"","injuries":"",
                        "notes":"","status":"Active","confidence":"High confidence|Needs review|Ambiguous"
                      }],
                      "memories":[{
                        "category":"Timeline|Lore|Relationship|Canon|Quest|World|Event",
                        "title":"...","content":"...",
                        "confidence":"High confidence|Needs review|Ambiguous"
                      }],
                      "locations":[{
                        "name":"...","region":"","parentLocation":"","description":"",
                        "discoveryState":"Unknown|Heard About|Discovered|Visited",
                        "status":"Active|Changed|Destroyed|Inaccessible","notes":"",
                        "confidence":"High confidence|Needs review|Ambiguous"
                      }],
                      "factions":[{
                        "name":"...","description":"","alignment":"",
                        "relationshipToParty":"Unknown|Friendly|Neutral|Hostile|Allied",
                        "status":"Active|Defeated|Disbanded|Unknown","goals":"","notes":"",
                        "confidence":"High confidence|Needs review|Ambiguous"
                      }],
                      "quests":[{
                        "title":"...","summary":"","status":"Active|Paused|Completed|Failed",
                        "objective":"","relatedLocation":"","relatedFaction":"",
                        "importance":"Critical|High|Normal|Low","notes":"",
                        "confidence":"High confidence|Needs review|Ambiguous"
                      }],
                      "timelineEvents":[{
                        "title":"...","summary":"",
                        "eventType":"Discovery|Battle|Decision|Relationship|Quest|World Change|Character Change|Other",
                        "location":"","involvedCharacters":"","storyArc":"",
                        "importance":"Critical|High|Normal|Low",
                        "confidence":"High confidence|Needs review|Ambiguous"
                      }]
                    }
                """.trimIndent()

                val raw = provider.generate(
                    ProviderRequest(
                        systemPrompt = system,
                        memoryContext = "",
                        messages = listOf(ProviderMessage("user", "SOURCE CAMPAIGN MATERIAL:\n$text"))
                    )
                )
                _externalImportDraft.value = ExternalCampaignImport.parseAnalysis(raw, text)
            } catch (t: Throwable) {
                _lastError.value = t.message ?: "External campaign analysis failed."
            } finally {
                _isImportAnalyzing.value = false
            }
        }
    }

    fun cancelExternalImport() {
        _externalImportDraft.value = null
    }

    fun commitExternalImport(draft: ExternalImportDraft) {
        viewModelScope.launch {
            try {
                val id = repository.commitExternalImport(draft)
                selectedId.value = id
                _externalImportDraft.value = null
                _notice.value = "External campaign imported. Review its characters and memories before continuing."
            } catch (t: Throwable) {
                _lastError.value = t.message ?: "Could not create imported campaign."
            }
        }
    }

    fun addMemory(t: String, c: String, cat: String = "Canon") {
        val id = selectedCampaign.value?.id ?: return
        if (t.isBlank() || c.isBlank()) return
        viewModelScope.launch { repository.addMemory(id, t, c, cat) }
    }

    fun deleteMemory(m: MemoryEntity) =
        viewModelScope.launch { repository.deleteMemory(m) }

    fun addCharacter(c: CharacterEntity) {
        val id = selectedCampaign.value?.id ?: return
        if (c.name.isBlank()) return
        viewModelScope.launch { repository.addCharacter(c.copy(campaignId = id)) }
    }

    fun updateCharacter(c: CharacterEntity) {
        if (c.name.isBlank()) return
        viewModelScope.launch { repository.updateCharacter(c) }
    }

    fun deleteCharacter(c: CharacterEntity) =
        viewModelScope.launch { repository.deleteCharacter(c) }

    fun approveProposal(p: ChangeProposalEntity, edited: String? = null) {
        viewModelScope.launch {
            try {
                repository.approveProposal(p, edited)
            } catch (t: Throwable) {
                _lastError.value = t.message ?: "Could not approve proposal."
            }
        }
    }

    fun rejectProposal(p: ChangeProposalEntity) =
        viewModelScope.launch { repository.rejectProposal(p) }

    fun approveAll(items: List<ChangeProposalEntity>) =
        viewModelScope.launch {
            try {
                repository.approveAll(items)
            } catch (t: Throwable) {
                _lastError.value = t.message ?: "Could not approve group."
            }
        }

    fun rejectAll(items: List<ChangeProposalEntity>) =
        viewModelScope.launch { repository.rejectAll(items) }

    fun sendMessage(text: String) {
        val campaign = selectedCampaign.value ?: return
        if (text.isBlank() || _isGenerating.value) return

        viewModelScope.launch {
            _isGenerating.value = true
            _lastError.value = null

            try {
                repository.addMessage(campaign.id, "user", text)

                // v0.9.3 fast path: explicit quest commands create/update Review proposals
                // without waiting for the general AI continuity analyzer.
                repository.proposeExplicitQuestCommand(campaign.id, text)

                val context = buildCampaignContext(campaign)

                val history = messages.value
                    .takeLast(40)
                    .map { ProviderMessage(it.role, it.content) }
                    .let { existing ->
                        if (
                            existing.lastOrNull()?.role == "user" &&
                            existing.lastOrNull()?.content == text
                        ) existing
                        else existing + ProviderMessage("user", text)
                    }

                val system = """
                    You are Chronicle's campaign storyteller and GM.

                    CANONICAL AUTHORITY
                    - The Chronicle database character records supplied in context are the ONLY authoritative character sheets.
                    - Never maintain a second independent character sheet inside chat.
                    - Never silently rewrite a canonical character record in narration.
                    - If the user asks you to CREATE or REGISTER a new character sheet, do NOT print a full authoritative sheet in chat.
                      Briefly acknowledge the request and continue naturally; Chronicle's separate Review system will propose the real character record.
                    - If the user asks to SHOW an existing character sheet, mirror ONLY the canonical database record supplied in context.
                      Label it as a read-only view and do not invent values for blank fields.
                    - Character changes happen through story events and Chronicle Review, not by editing an unofficial chat sheet.

                    QUEST / WORLD AUTHORITY
                    - Chronicle's World > Quests records are the ONLY authoritative quest tracker.
                    - If the user asks to add/start/create/complete/fail/pause/update a quest, do NOT create or maintain an authoritative quest card/list/JSON/block inside Chat.
                    - Briefly acknowledge the story/command naturally. Chronicle Review handles the actual quest record.
                    - If the user asks to SHOW quests, mirror only the canonical quest records supplied in context as a read-only view.
                    - Never claim a quest was saved merely because you formatted it in Chat.

                    STORY RULES
                    Preserve established canon, causal continuity, character autonomy, and consequences.
                    Never import facts from another campaign.
                    Treat supplied campaign and canonical character data as authoritative.
                    Cast Tier indicates narrative importance, not moral worth.
                    If something is not established here, do not pretend you remember it.
                """.trimIndent()

                val provider: AiProvider =
                    if (_providerSettings.value.enabled) {
                        OpenAiCompatibleProvider { settingsStore.load() }
                    } else {
                        ChronicleDemoProvider()
                    }

                val reply = provider.generate(
                    ProviderRequest(
                        systemPrompt = system,
                        memoryContext = context,
                        messages = history
                    )
                )

                repository.addMessage(campaign.id, "assistant", reply)

                if (_providerSettings.value.enabled) {
                    // v0.9.1: continuity/world analysis is part of normal real-AI play.
                    // The manual Scan button remains available as a recovery/re-scan tool.
                    scanForProposals(
                        campaign,
                        repository.buildAutomationContextSnapshot(campaign),
                        text,
                        reply,
                        provider
                    )
                }
            } catch (t: Throwable) {
                _lastError.value = t.message ?: "Unknown AI provider error."
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun scanLastExchangeForProposals() {
        val campaign = selectedCampaign.value ?: return
        if (!_providerSettings.value.enabled) return

        if (_isReviewScanning.value) {
            _queuedManualRescan = true
            _notice.value = "Scan queued. Chronicle will rescan the latest exchange when the current scan finishes."
            return
        }

        val lastUser = messages.value.lastOrNull { it.role == "user" } ?: return
        val lastAssistant = messages.value.lastOrNull {
            it.role == "assistant" && it.createdAt >= lastUser.createdAt
        } ?: return

        viewModelScope.launch {
            val provider: AiProvider = OpenAiCompatibleProvider { settingsStore.load() }
            scanForProposals(
                campaign,
                repository.buildAutomationContextSnapshot(campaign),
                lastUser.content,
                lastAssistant.content,
                provider
            )
        }
    }

    private suspend fun scanForProposals(
        campaign: CampaignEntity,
        context: String,
        userText: String,
        assistantReply: String,
        provider: AiProvider
    ) {
        if (_isReviewScanning.value) return
        _isReviewScanning.value = true

        try {
            val analyzerSystem = """
                You are Chronicle's continuity and canonical-record change detector.
                Return ONLY a JSON array of proposed persistent changes from the latest exchange.

                AUTHORITY RULES
                - You may PROPOSE. You never decide canon.
                - Chronicle's existing database character records are authoritative.
                - Never create a duplicate character record for a name already present in context.
                - If a character already exists, use character_update with that exact targetId.
                - If the user asks to create/register a NEW character sheet and no matching canonical character exists,
                  use character_new. The storyteller's chat response is NOT the character sheet.
                - Do not treat a character sheet printed by the assistant in chat as authoritative evidence by itself.
                - Assistant-invented identity facts are Assistant Only unless the user confirms them or a resolved story event establishes them.

                EVIDENCE TYPE
                Player Confirmed = explicitly stated/confirmed by the user as canon or a desired persistent fact. If the user supplies the fact and the assistant only reformats or repeats it, it is Player Confirmed, NOT Assistant Only.
                Story Event = a resolved event/consequence actually occurred in the exchange.
                Assistant Only = introduced only by the storyteller/AI without user confirmation.
                Unverified = source is unclear or conflicting.

                CHANGE MODE
                Append = add development while preserving established content. PREFER for personality growth,
                relationships, goals, fears, injuries, notes, affiliations, additive equipment, and additive abilities.
                Replace = genuine correction, permanent transformation, exact state replacement, or explicit user rewrite.
                Clear = explicit removal/loss of the field's value.
                character_new always uses Replace.

                AUTOMATION COVERAGE CHECKLIST
                Before returning, explicitly check the latest exchange against ALL of these categories:
                1. Existing character updates
                2. New canonical characters
                3. Location discovery/status/change
                4. Faction creation/status/relationship change
                5. Existing quest lifecycle change: Active, Paused, Completed, Failed
                6. New quest/story thread
                7. Campaign current location/objective changes
                8. Meaningful timeline milestone
                9. Durable memory/lore/relationship facts

                EXISTING QUEST LIFECYCLE
                - When a quest already exists in WORLD STATE, REUSE ITS EXACT TITLE from context.
                - Do not create a second quest merely because the wording changed.
                - If the objective was achieved, propose quest_upsert with status Completed.
                - If it definitively failed, propose status Failed.
                - If it is deliberately suspended, propose status Paused.
                - Preserve the same quest identity and update its other fields only when the exchange establishes a change.
                - A completed/failed quest may also justify a separate timeline_event_new if the outcome is a meaningful story milestone.

                CORE RULES
                - Do not speculate.
                - Ignore jokes, hypotheticals, plans, possibilities, questions, and uncertain statements.
                - Avoid information already stored in campaign context.
                - Prefer durable continuity over temporary flavor.
                - Maximum 12 proposals.

                CAST TIERS
                Main: core protagonists/companions.
                Secondary: recurring important allies, rivals, family, villains, mentors.
                Supporting: recurring arc-relevant characters.
                Background: incidental characters. Do not create trivial background records unless recurring or consequential.

                PRIORITY
                Critical = death/resurrection, permanent transformation, catastrophic world change,
                major betrayal, core relationship creation/destruction, major canon contradiction.
                High = major injury/healing, new permanent power, major quest outcome, important reveal,
                major relationship development, meaningful cast-tier promotion.
                Normal = durable goals, equipment, affiliations, recurring development, relevant lore.
                Low = useful but nonessential persistent detail.

                ALLOWED TARGETS

                character_new
                targetId=null
                changes={
                  "name":"...",
                  "aliases":"",
                  "species":"",
                  "age":"",
                  "pronouns":"",
                  "appearance":"",
                  "personality":"",
                  "backstory":"",
                  "abilities":"",
                  "equipment":"",
                  "relationship":"",
                  "affiliations":"",
                  "goals":"",
                  "fears":"",
                  "secrets":"",
                  "injuries":"",
                  "notes":"",
                  "status":"Active",
                  "castTier":"Main|Secondary|Supporting|Background"
                }

                character_update
                targetId MUST match an existing canonical character ID.
                changes={"fields":{"fieldName":"NEW OR ADDITIVE VALUE"}}
                Allowed fields: name, aliases, species, age, pronouns, appearance, personality,
                backstory, abilities, equipment, relationship, affiliations, goals, fears,
                secrets, injuries, notes, status.

                memory_new
                changes={"category":"Timeline|Lore|Relationship|Canon|Quest","title":"...","content":"..."}

                campaign_update
                changes={"fields":{"currentLocation":"...","currentObjective":"..."}}

                cast_tier_update
                changes={"castTier":"Main|Secondary|Supporting|Background"}

                WORLD STATE TARGETS

                location_upsert
                changes={"name":"...","region":"...","parentLocation":"...","description":"...","discoveryState":"Unknown|Heard About|Discovered|Visited","status":"Active|Changed|Destroyed|Inaccessible","notes":"..."}
                Use only for locations that matter to continuity. Prefer Story Event when the party actually discovers/visits/changes it.

                faction_upsert
                changes={"name":"...","description":"...","alignment":"...","relationshipToParty":"Unknown|Friendly|Neutral|Hostile|Allied","status":"Active|Defeated|Disbanded|Unknown","goals":"...","notes":"..."}

                quest_upsert
                changes={"title":"...","summary":"...","status":"Active|Paused|Completed|Failed","objective":"...","relatedLocation":"...","relatedFaction":"...","importance":"Critical|High|Normal|Low","notes":"..."}

                timeline_event_new
                changes={"title":"...","summary":"...","eventType":"Discovery|Battle|Decision|Relationship|Quest|World Change|Character Change|Other","location":"...","involvedCharacters":"comma-separated names","storyArc":"...","importance":"Critical|High|Normal|Low"}
                Timeline events are for meaningful story beats only. Do not create one for routine dialogue or minor flavor.

                EVERY OBJECT:
                {
                  "summary":"plain English",
                  "targetType":"...",
                  "targetId":123 or null,
                  "reason":"specific evidence from the latest exchange",
                  "priority":"Critical|High|Normal|Low",
                  "groupType":"Characters|World|Events|Lore|Relationships|Quests|Other",
                  "groupLabel":"character name, location, arc, faction, relationship, or short folder label",
                  "changeMode":"Append|Replace|Clear",
                  "evidenceType":"Player Confirmed|Story Event|Assistant Only|Unverified",
                  "changes":{...}
                }

                Return [] only after checking every category in the automation coverage checklist against the fresh canonical context.
                If an existing canonical record changed state, do not return [] merely because no new record was created.
                If nothing deserves persistence, return [].
            """.trimIndent()

            val raw = provider.generate(
                ProviderRequest(
                    systemPrompt = analyzerSystem,
                    memoryContext = context,
                    messages = listOf(
                        ProviderMessage(
                            "user",
                            "LATEST USER MESSAGE:\n$userText\n\nLATEST STORYTELLER REPLY:\n$assistantReply"
                        )
                    )
                )
            )

            ProposalParser.parse(raw).forEach { parsed ->
                val targetCharacter = parsed.targetId?.let { id ->
                    characters.value.firstOrNull { it.id == id }
                }

                val passesImpactGate =
                    if (
                        parsed.targetType == "character_update" &&
                        targetCharacter?.castTier == "Background"
                    ) {
                        parsed.priority in setOf("Critical", "High")
                    } else true

                if (passesImpactGate) {
                    repository.addProposal(
                        ChangeProposalEntity(
                            campaignId = campaign.id,
                            summary = parsed.summary,
                            targetType = parsed.targetType,
                            targetId = parsed.targetId,
                            proposedChanges = parsed.proposedChanges,
                            reason = parsed.reason,
                            priority = parsed.priority,
                            groupType = parsed.groupType,
                            groupLabel = parsed.groupLabel.ifBlank {
                                targetCharacter?.name.orEmpty()
                            },
                            changeMode = parsed.changeMode,
                            evidenceType = parsed.evidenceType
                        )
                    )
                }
            }
        } catch (_: Throwable) {
            // Proposal extraction never interrupts active roleplay.
        } finally {
            _isReviewScanning.value = false

            if (_queuedManualRescan) {
                _queuedManualRescan = false
                scanLastExchangeForProposals()
            }
        }
    }

    private fun buildCampaignContext(campaign: CampaignEntity): String = buildString {
        appendLine("CAMPAIGN: ${campaign.name}")
        if (campaign.description.isNotBlank()) appendLine("Description: ${campaign.description}")
        if (campaign.setting.isNotBlank()) appendLine("Setting: ${campaign.setting}")
        if (campaign.genreTone.isNotBlank()) appendLine("Genre/Tone: ${campaign.genreTone}")
        if (campaign.currentLocation.isNotBlank()) appendLine("Current location: ${campaign.currentLocation}")
        if (campaign.currentObjective.isNotBlank()) appendLine("Current objective: ${campaign.currentObjective}")

        appendLine("WORLD STATE:")
        locations.value.forEach { l ->
            appendLine("LOCATION | ${l.name} | region=${l.region} | parent=${l.parentLocation} | discovery=${l.discoveryState} | status=${l.status} | ${l.description}")
        }
        factions.value.forEach { f ->
            appendLine("FACTION | ${f.name} | relationship=${f.relationshipToParty} | status=${f.status} | goals=${f.goals} | ${f.description}")
        }
        quests.value.forEach { q ->
            appendLine("QUEST | ${q.title} | status=${q.status} | importance=${q.importance} | objective=${q.objective} | location=${q.relatedLocation} | faction=${q.relatedFaction}")
        }
        timelineEvents.value.take(30).reversed().forEach { e ->
            appendLine("TIMELINE | ${e.title} | ${e.eventType} | importance=${e.importance} | location=${e.location} | ${e.summary}")
        }
        appendLine()

        if (memories.value.isNotEmpty()) {
            appendLine("MEMORIES:")
            memories.value.forEach {
                appendLine("[${it.category}] ${it.title}: ${it.content}")
            }
        }

        if (characters.value.isNotEmpty()) {
            appendLine("CANONICAL CHARACTER RECORDS — DATABASE SOURCE OF TRUTH:")
            characters.value.forEach { c ->
                appendLine(
                     "${c.name} [ID ${c.id}] [CAST ${c.castTier}] [INTEGRITY ${c.integrityMode}] | aliases=${c.aliases} | species=${c.species} | " +
                        "age=${c.age} | pronouns=${c.pronouns} | appearance=${c.appearance} | " +
                        "personality=${c.personality} | backstory=${c.backstory} | abilities=${c.abilities} | " +
                        "equipment=${c.equipment} | relationship=${c.relationship} | affiliations=${c.affiliations} | " +
                        "goals=${c.goals} | fears=${c.fears} | secrets=${c.secrets} | injuries=${c.injuries} | " +
                        "notes=${c.notes} | status=${c.status}"
                )
            }
        }
    }

    class Factory(
        private val repository: ChronicleRepository,
        private val settingsStore: ProviderSettingsStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ChronicleViewModel(repository, settingsStore) as T
    }
}
