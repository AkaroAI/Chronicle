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
    private val provider: AiProvider = ChronicleDemoProvider()
) : ViewModel() {

    val campaigns = repository.campaigns()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedCampaignId = MutableStateFlow<Long?>(null)
    val selectedCampaignId: StateFlow<Long?> = _selectedCampaignId.asStateFlow()

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

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()

    init {
        viewModelScope.launch {
            campaigns.collect { list ->
                if (_selectedCampaignId.value == null && list.isNotEmpty()) {
                    _selectedCampaignId.value = list.first().id
                }
            }
        }
    }

    fun selectCampaign(id: Long) {
        _selectedCampaignId.value = id
    }

    fun createCampaign(name: String, description: String = "") {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = repository.createCampaign(name, description)
            _selectedCampaignId.value = id
        }
    }

    fun deleteCampaign(campaign: CampaignEntity) {
        viewModelScope.launch {
            repository.deleteCampaign(campaign)
            if (_selectedCampaignId.value == campaign.id) {
                _selectedCampaignId.value = null
            }
        }
    }

    fun addMemory(title: String, content: String, category: String = "Canon") {
        val id = selectedCampaign.value?.id ?: return
        if (title.isBlank() || content.isBlank()) return
        viewModelScope.launch {
            repository.addMemory(id, title, content, category)
        }
    }

    fun deleteMemory(memory: MemoryEntity) {
        viewModelScope.launch { repository.deleteMemory(memory) }
    }

    fun addCharacter(name: String, summary: String, relationship: String) {
        val id = selectedCampaign.value?.id ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.addCharacter(id, name, summary, relationship)
        }
    }

    fun deleteCharacter(character: CharacterEntity) {
        viewModelScope.launch { repository.deleteCharacter(character) }
    }

    fun sendMessage(text: String) {
        val campaign = selectedCampaign.value ?: return
        if (text.isBlank() || _isGenerating.value) return

        viewModelScope.launch {
            _isGenerating.value = true
            try {
                repository.addMessage(campaign.id, "user", text)

                val memoryContext = memories.value.joinToString("\n\n") {
                    "[${it.category}] ${it.title}: ${it.content}"
                }

                val recent = messages.value.takeLast(40).map {
                    ProviderMessage(role = it.role, content = it.content)
                } + ProviderMessage("user", text)

                val systemPrompt = buildString {
                    appendLine("You are the Chronicle campaign storyteller and GM.")
                    appendLine("Maintain continuity, character autonomy, consequences, and established canon.")
                    appendLine("Do not mix information from any other campaign.")
                    appendLine("Treat the supplied memory as campaign-specific canon unless contradicted by newer user instructions.")
                }

                val response = provider.generate(
                    ProviderRequest(
                        systemPrompt = systemPrompt,
                        memoryContext = memoryContext,
                        messages = recent
                    )
                )

                repository.addMessage(campaign.id, "assistant", response)
            } finally {
                _isGenerating.value = false
            }
        }
    }

    class Factory(private val repository: ChronicleRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChronicleViewModel(repository) as T
        }
    }
}
