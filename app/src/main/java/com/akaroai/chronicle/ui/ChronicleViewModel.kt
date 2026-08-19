package com.akaroai.chronicle.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.akaroai.chronicle.data.ChronicleRepository
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

    private val _providerSettings = MutableStateFlow(settingsStore.load())
    val providerSettings = _providerSettings.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()

    private val _isReviewScanning = MutableStateFlow(false)
    val isReviewScanning = _isReviewScanning.asStateFlow()

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
                    Preserve established canon, causal continuity, character autonomy, and consequences.
                    Never import facts from another campaign.
                    Treat supplied campaign and character-sheet data as authoritative.
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

                if (
                    _providerSettings.value.enabled &&
                    _providerSettings.value.autoReviewEnabled
                ) {
                    scanForProposals(campaign, context, text, reply, provider)
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
        if (!_providerSettings.value.enabled || _isReviewScanning.value) return

        val lastUser = messages.value.lastOrNull { it.role == "user" } ?: return
        val lastAssistant = messages.value.lastOrNull {
            it.role == "assistant" && it.createdAt >= lastUser.createdAt
        } ?: return

        viewModelScope.launch {
            val provider: AiProvider = OpenAiCompatibleProvider { settingsStore.load() }
            scanForProposals(
                campaign,
                buildCampaignContext(campaign),
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
                You are Chronicle's continuity change detector.
                Return ONLY a JSON array of proposed persistent changes from the latest exchange.

                CORE RULES
                - You may PROPOSE. You never decide canon.
                - Do not speculate.
                - Ignore jokes, hypotheticals, plans, possibilities, questions, and uncertain statements.
                - Avoid information already stored in campaign context.
                - Prefer durable continuity over temporary flavor.
                - Maximum 12 proposals.

                CAST TIERS
                Main: core protagonists/companions. Track meaningful emotional, physical, relational,
                ability, equipment, goal, secret, and status changes closely.
                Secondary: recurring important allies, rivals, family, villains, mentors. Track meaningful changes.
                Supporting: arc-relevant recurring characters. Track only changes likely to matter again.
                Background: incidental characters. DO NOT create character_update proposals for them unless
                their change is HIGH or CRITICAL and directly impacts Main cast, world state, a major event,
                quest, faction, location, or important lore.

                IMPORTANT DISTINCTION
                A background character can reveal or cause important WORLD/EVENT/LORE facts.
                In that case file the proposal under World, Events, Lore, Relationships, or Quests rather
                than creating trivial background-character records.

                PRIORITY
                Critical = death/resurrection, permanent transformation, catastrophic world change,
                major betrayal, core relationship creation/destruction, major canon contradiction.
                High = major injury/healing, new permanent power, major quest outcome, important reveal,
                major relationship development, meaningful cast-tier promotion.
                Normal = durable goals, equipment, affiliations, recurring development, relevant lore.
                Low = useful but nonessential persistent detail. Avoid low-value background noise.

                GROUP TYPES
                Characters, World, Events, Lore, Relationships, Quests, Other

                ALLOWED TARGETS

                memory_new
                changes={"category":"Timeline|Lore|Relationship|Canon|Quest","title":"...","content":"..."}

                character_update
                targetId MUST match an existing character ID.
                changes={"fields":{"fieldName":"COMPLETE NEW VALUE"}}
                Allowed fields: name, aliases, species, age, pronouns, appearance, personality,
                backstory, abilities, equipment, relationship, affiliations, goals, fears,
                secrets, injuries, notes, status.

                campaign_update
                changes={"fields":{"currentLocation":"...","currentObjective":"..."}}
                Allowed fields: name, description, setting, genreTone, currentLocation, currentObjective.

                cast_tier_update
                ONLY when repeated story importance clearly changed.
                changes={"castTier":"Main|Secondary|Supporting|Background"}

                EVERY OBJECT:
                {
                  "summary":"plain English",
                  "targetType":"...",
                  "targetId":123 or null,
                  "reason":"evidence from latest exchange",
                  "priority":"Critical|High|Normal|Low",
                  "groupType":"Characters|World|Events|Lore|Relationships|Quests|Other",
                  "groupLabel":"character name, location, arc, faction, relationship, or short folder label",
                  "changes":{...}
                }

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
                            }
                        )
                    )
                }
            }
        } catch (_: Throwable) {
            // Proposal extraction never interrupts active roleplay.
        } finally {
            _isReviewScanning.value = false
        }
    }

    private fun buildCampaignContext(campaign: CampaignEntity): String = buildString {
        appendLine("CAMPAIGN: ${campaign.name}")
        if (campaign.description.isNotBlank()) appendLine("Description: ${campaign.description}")
        if (campaign.setting.isNotBlank()) appendLine("Setting: ${campaign.setting}")
        if (campaign.genreTone.isNotBlank()) appendLine("Genre/Tone: ${campaign.genreTone}")
        if (campaign.currentLocation.isNotBlank()) appendLine("Current location: ${campaign.currentLocation}")
        if (campaign.currentObjective.isNotBlank()) appendLine("Current objective: ${campaign.currentObjective}")

        if (memories.value.isNotEmpty()) {
            appendLine("MEMORIES:")
            memories.value.forEach {
                appendLine("[${it.category}] ${it.title}: ${it.content}")
            }
        }

        if (characters.value.isNotEmpty()) {
            appendLine("CHARACTERS:")
            characters.value.forEach { c ->
                appendLine(
                    "${c.name} [ID ${c.id}] [CAST ${c.castTier}] | aliases=${c.aliases} | species=${c.species} | " +
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
