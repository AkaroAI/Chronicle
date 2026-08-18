package com.akaroai.chronicle.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedCampaignId = MutableStateFlow<Long?>(null)

    val selectedCampaign: StateFlow<CampaignEntity?> =
        combine(campaigns, _selectedCampaignId) { list, id ->
            list.firstOrNull { it.id == id } ?: list.firstOrNull()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val messages: StateFlow<List<MessageEntity>> =
        selectedCampaign.filterNotNull()
            .flatMapLatest { repository.messages(it.id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val memories: StateFlow<List<MemoryEntity>> =
        selectedCampaign.filterNotNull()
            .flatMapLatest { repository.memories(it.id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val characters: StateFlow<List<CharacterEntity>> =
        selectedCampaign.filterNotNull()
            .flatMapLatest { repository.characters(it.id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _providerSettings = MutableStateFlow(settingsStore.load())
    val providerSettings = _providerSettings.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError = _lastError.asStateFlow()

    init {
        viewModelScope.launch {
            campaigns.collect { list ->
                if (_selectedCampaignId.value == null && list.isNotEmpty()) {
                    _selectedCampaignId.value = list.first().id
                }
            }
        }
    }

    fun clearError() { _lastError.value = null }

    fun saveProviderSettings(settings: ProviderSettings) {
        settingsStore.save(settings)
        _providerSettings.value = settingsStore.load()
    }

    fun selectCampaign(id: Long) { _selectedCampaignId.value = id }

    fun createCampaign(name: String, description: String = "") {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = repository.createCampaign(name, description)
            _selectedCampaignId.value = id
        }
    }

    fun addMemory(title: String, content: String, category: String = "Canon") {
        val id = selectedCampaign.value?.id ?: return
        if (title.isBlank() || content.isBlank()) return
        viewModelScope.launch { repository.addMemory(id, title, content, category) }
    }

    fun deleteMemory(memory: MemoryEntity) {
        viewModelScope.launch { repository.deleteMemory(memory) }
    }

    fun addCharacter(name: String, summary: String, relationship: String) {
        val id = selectedCampaign.value?.id ?: return
        if (name.isBlank()) return
        viewModelScope.launch { repository.addCharacter(id, name, summary, relationship) }
    }

    fun deleteCharacter(character: CharacterEntity) {
        viewModelScope.launch { repository.deleteCharacter(character) }
    }

    fun sendMessage(text: String) {
        val campaign = selectedCampaign.value ?: return
        if (text.isBlank() || _isGenerating.value) return

        viewModelScope.launch {
            _isGenerating.value = true
            _lastError.value = null

            try {
                repository.addMessage(campaign.id, "user", text)

                val memoryContext = buildString {
                    if (memories.value.isNotEmpty()) {
                        appendLine("MEMORIES:")
                        memories.value.forEach {
                            appendLine("[${it.category}] ${it.title}: ${it.content}")
                        }
                    }

                    if (characters.value.isNotEmpty()) {
                        appendLine()
                        appendLine("CHARACTERS:")
                        characters.value.forEach {
                            appendLine(
                                "${it.name} | relationship=${it.relationship.ifBlank { "unspecified" }} | " +
                                    "status=${it.status} | notes=${it.summary}"
                            )
                        }
                    }
                }

                val history = messages.value.takeLast(40).map {
                    ProviderMessage(it.role, it.content)
                } + ProviderMessage("user", text)

                val systemPrompt = """
                    You are Chronicle's campaign storyteller and GM.
                    Preserve established canon, causal continuity, character autonomy, and consequences.
                    Never import facts, characters, relationships, or events from another campaign.
                    If something is not established in this campaign's supplied context, do not pretend you remember it.
                    Stay immersed unless the user clearly speaks out of character.
                """.trimIndent()

                val provider: AiProvider =
                    if (_providerSettings.value.enabled) {
                        OpenAiCompatibleProvider { settingsStore.load() }
                    } else {
                        ChronicleDemoProvider()
                    }

                val response = provider.generate(
                    ProviderRequest(
                        systemPrompt = systemPrompt,
                        memoryContext = memoryContext,
                        messages = history
                    )
                )

                repository.addMessage(campaign.id, "assistant", response)
            } catch (t: Throwable) {
                _lastError.value = t.message ?: "Unknown AI provider error."
            } finally {
                _isGenerating.value = false
            }
        }
    }

    class Factory(
        private val repository: ChronicleRepository,
        private val settingsStore: ProviderSettingsStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChronicleViewModel(repository, settingsStore) as T
        }
    }
}
