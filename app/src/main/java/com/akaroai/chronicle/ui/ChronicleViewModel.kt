package com.akaroai.chronicle.ui

import androidx.lifecycle.*
import com.akaroai.chronicle.data.ChronicleRepository
import com.akaroai.chronicle.model.*
import com.akaroai.chronicle.provider.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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

    fun approveProposal(proposal: ChangeProposalEntity, editedChanges: String? = null) {
        viewModelScope.launch {
            try {
                repository.approveProposal(proposal, editedChanges)
            } catch (t: Throwable) {
                _lastError.value = t.message ?: "Could not approve proposal."
            }
        }
    }

    fun rejectProposal(proposal: ChangeProposalEntity) {
        viewModelScope.launch { repository.rejectProposal(proposal) }
    }

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
                        if (existing.lastOrNull()?.role == "user" &&
                            existing.lastOrNull()?.content == text
                        ) existing else existing + ProviderMessage("user", text)
                    }

                val system = """
                    You are Chronicle's campaign storyteller and GM.
                    Preserve established canon, causal continuity, character autonomy, and consequences.
                    Never import facts from another campaign.
                    Treat supplied campaign and character-sheet data as authoritative.
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

                if (_providerSettings.value.enabled &&
                    _providerSettings.value.autoReviewEnabled
                ) {
                    scanForProposals(
                        campaign = campaign,
                        context = context,
                        userText = text,
                        assistantReply = reply,
                        provider = provider
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
        if (!_providerSettings.value.enabled || _isReviewScanning.value) return

        val lastUser = messages.value.lastOrNull { it.role == "user" } ?: return
        val lastAssistant = messages.value.lastOrNull {
            it.role == "assistant" && it.createdAt >= lastUser.createdAt
        } ?: return

        viewModelScope.launch {
            val provider: AiProvider = OpenAiCompatibleProvider { settingsStore.load() }
            scanForProposals(
                campaign = campaign,
                context = buildCampaignContext(campaign),
                userText = lastUser.content,
                assistantReply = lastAssistant.content,
                provider = provider
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
                Your ONLY job is to identify clearly established story facts from the latest exchange
                that are worth proposing as persistent campaign data.

                IMPORTANT:
                - Never modify canon yourself.
                - Return proposals only; the user will approve, edit, or reject them.
                - Do not speculate.
                - Do not convert jokes, questions, possibilities, hypotheticals, plans, or uncertain claims into facts.
                - Avoid proposing information already present in the supplied campaign context.
                - Prefer meaningful continuity facts over trivial details.
                - Maximum 5 proposals.
                - Return ONLY a JSON array. No markdown and no commentary.

                Allowed target types:

                1) memory_new
                   changes:
                   {"category":"Timeline|Lore|Relationship|Canon|Quest","title":"short title","content":"fact to remember"}

                2) character_update
                   targetId MUST be an existing character ID from the supplied context.
                   changes:
                   {"fields":{"fieldName":"complete new field value"}}
                   Allowed fields: name, aliases, species, age, pronouns, appearance, personality,
                   backstory, abilities, equipment, relationship, affiliations, goals, fears,
                   secrets, injuries, notes, status.

                3) campaign_update
                   changes:
                   {"fields":{"currentLocation":"...","currentObjective":"..."}}
                   Allowed fields: name, description, setting, genreTone, currentLocation, currentObjective.

                Every proposal object MUST have:
                {
                  "summary":"plain-English explanation of what will change",
                  "targetType":"memory_new|character_update|campaign_update",
                  "targetId":123 or null,
                  "reason":"what in the latest exchange supports this",
                  "changes":{...}
                }

                If there is nothing worth saving, return [].
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

            val parsed = ProposalParser.parse(raw)
            parsed.forEach {
                repository.addProposal(
                    ChangeProposalEntity(
                        campaignId = campaign.id,
                        summary = it.summary,
                        targetType = it.targetType,
                        targetId = it.targetId,
                        proposedChanges = it.proposedChanges,
                        reason = it.reason
                    )
                )
            }
        } catch (_: Throwable) {
            // Review extraction is intentionally non-blocking.
            // A failed analyzer call should never interrupt the campaign chat.
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
                    "${c.name} [ID ${c.id}] | aliases=${c.aliases} | species=${c.species} | " +
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
