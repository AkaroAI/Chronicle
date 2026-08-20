package com.akaroai.chronicle.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.akaroai.chronicle.data.ChronicleRepository
import com.akaroai.chronicle.data.ExternalCampaignImport
import com.akaroai.chronicle.data.ExternalImportDraft
import com.akaroai.chronicle.data.ImportedLocationDraft
import com.akaroai.chronicle.model.*
import com.akaroai.chronicle.provider.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

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

    private val _pendingCanonTurn = MutableStateFlow<String?>(null)
    val pendingCanonTurn = _pendingCanonTurn.asStateFlow()
    private var _pendingTurnProposalIds: Set<Long> = emptySet()

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

    private fun seedMissingLocationsFromSource(
        draft: ExternalImportDraft,
        sourceText: String
    ): ExternalImportDraft {
        fun normalize(value: String): String = value.lowercase()
            .replace(Regex("""\bthe\b"""), " ")
            .replace(Regex("""\beastern\b"""), " east ")
            .replace(Regex("""\bwestern\b"""), " west ")
            .replace(Regex("""\bnorthern\b"""), " north ")
            .replace(Regex("""\bsouthern\b"""), " south ")
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")

        fun displayName(raw: String): String {
            val cleaned = raw.trim().trim('.', ',', ';', ':', '"', '\'')
                .replace(Regex("""(?i)^the\s+"""), "")
                .replace(Regex("""(?i)^eastern\s+"""), "East ")
                .replace(Regex("""(?i)^western\s+"""), "West ")
                .replace(Regex("""(?i)^northern\s+"""), "North ")
                .replace(Regex("""(?i)^southern\s+"""), "South ")
            return cleaned.split(Regex("""\s+""")).joinToString(" ") { word ->
                if (word.isBlank()) word
                else word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        }

        val placeNoun = Regex(
            """(?i)\b(gate|village|town|city|inn|observatory|academy|chapel|temple|mine|harbor|castle|palace|fortress|tower|bridge|road|woods|wood|forest|ruins|ruin|camp|district|mountain|pass|valley|island|port)\b"""
        )

        val candidates = linkedSetOf<String>()
        if (draft.currentLocation.isNotBlank()) candidates += draft.currentLocation

        Regex("""(?im)^\s*Current Location\s*:\s*([^\r\n]+)""")
            .findAll(sourceText)
            .forEach { candidates += it.groupValues[1].trim() }

        val directionalPlace = Regex(
            """(?i)\b(?:to|toward|towards|at|into|through|near|outside|inside|in)\s+(?:the\s+)?((?:eastern|western|northern|southern|east|west|north|south)\s+(?:gate|road|woods?|forest|bridge|tower|ruins?|village|town|city|observatory|academy|chapel|temple|mine|harbor|inn|castle|palace|fortress))\b"""
        )
        directionalPlace.findAll(sourceText).forEach {
            candidates += displayName(it.groupValues[1])
        }

        val namedPlace = Regex(
            """\b([A-Z][A-Za-z'’-]+(?:\s+[A-Z][A-Za-z'’-]+){0,3}\s+(?:Gate|Village|Town|City|Inn|Observatory|Academy|Chapel|Temple|Mine|Harbor|Castle|Palace|Fortress|Tower|Bridge|Road|Woods|Forest|Ruins|Camp|District|Mountain|Pass|Valley|Island|Port))\b"""
        )
        namedPlace.findAll(sourceText).forEach {
            candidates += displayName(it.groupValues[1])
        }

        val merged = draft.locations.toMutableList()
        val identities = merged.map { normalize(it.name) }.toMutableSet()

        for (raw in candidates) {
            val name = displayName(raw)
            if (name.isBlank() || !placeNoun.containsMatchIn(name)) continue
            val identity = normalize(name)
            if (identity.isBlank() || identity in identities) continue

            merged += ImportedLocationDraft(
                name = name,
                description = "Recovered directly from the source text during import; verify this location in Import Review.",
                discoveryState = if (
                    sourceText.contains(Regex("""(?i)\b(arrive|arrives|arrived|rush|rushes|rushed|enter|enters|entered|reach|reaches|reached|at)\b.{0,45}\b${Regex.escape(name)}\b"""))
                ) "Visited" else "Discovered",
                status = "Active",
                confidence = "Needs review"
            )
            identities += identity
        }

        return draft.copy(locations = merged)
    }


    private fun seedImportedCharacterPresence(draft: ExternalImportDraft): ExternalImportDraft {
        fun normalize(value: String): String = value.lowercase()
            .replace(Regex("""\bthe\b"""), " ")
            .replace(Regex("""\beastern\b"""), " east ")
            .replace(Regex("""\bwestern\b"""), " west ")
            .replace(Regex("""\bnorthern\b"""), " north ")
            .replace(Regex("""\bsouthern\b"""), " south ")
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")

        val canonicalLocations = draft.locations.associateBy { normalize(it.name) }
        val source = draft.sourceText

        val updatedCharacters = draft.characters.map { character ->
            val explicitExisting = Regex("""(?i)Currently at\s+([^.\n]+)\.""")
                .findAll(character.notes)
                .lastOrNull()
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                .orEmpty()

            if (explicitExisting.isNotBlank()) {
                character
            } else {
                val name = Regex.escape(character.name)
                val candidates = mutableListOf<Pair<Int, String>>()

                canonicalLocations.values.forEach { location ->
                    val loc = Regex.escape(location.name)
                    val patterns = listOf(
                        Regex("""(?is)\b$name\b.{0,90}\b(?:travel(?:s|ed)?|go(?:es|ne)?|went|head(?:s|ed)?|move(?:s|d)?|arrive(?:s|d)?|return(?:s|ed)?|stay(?:s|ed)?|remain(?:s|ed)?|is|wait(?:s|ed|ing)?)\b.{0,45}\b(?:at|in|to|into|inside|near)?\s*(?:the\s+)?$loc\b"""),
                        Regex("""(?is)\b$name\b.{0,45}\b(?:at|in|inside|near)\s+(?:the\s+)?$loc\b""")
                    )
                    patterns.forEach { pattern ->
                        pattern.findAll(source).forEach { match ->
                            candidates += match.range.first to location.name
                        }
                    }
                }

                val latest = candidates.maxByOrNull { it.first }?.second
                if (latest.isNullOrBlank()) {
                    character
                } else {
                    character.copy(
                        notes = listOf(character.notes.trim(), "Currently at $latest.")
                            .filter { it.isNotBlank() }
                            .joinToString("\n"),
                        confidence = if (character.confidence == "Ambiguous") {
                            "Ambiguous"
                        } else {
                            "Needs review"
                        }
                    )
                }
            }
        }

        return draft.copy(characters = updatedCharacters)
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
                    For every named character, determine their LAST explicitly established current location when possible.
                    If confidently established, append the exact sentence "Currently at LOCATION_NAME." to that character's notes.
                    If current location is unknown or only historical, do not guess.
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
                        "notes":"","currentLocation":"","status":"Active","confidence":"High confidence|Needs review|Ambiguous"
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
                val parsedDraft = seedMissingLocationsFromSource(
                    ExternalCampaignImport.parseAnalysis(raw, text),
                    text
                )
                _externalImportDraft.value = seedImportedCharacterPresence(parsedDraft)
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
                maybeResumePendingCanonTurn()
            } catch (t: Throwable) {
                _lastError.value = t.message ?: "Could not approve proposal."
            }
        }
    }

    fun rejectProposal(p: ChangeProposalEntity) =
        viewModelScope.launch {
            repository.rejectProposal(p)
            maybeResumePendingCanonTurn()
        }

    fun approveAll(items: List<ChangeProposalEntity>) =
        viewModelScope.launch {
            try {
                repository.approveAll(items)
                maybeResumePendingCanonTurn()
            } catch (t: Throwable) {
                _lastError.value = t.message ?: "Could not approve group."
            }
        }

    fun rejectAll(items: List<ChangeProposalEntity>) =
        viewModelScope.launch {
            repository.rejectAll(items)
            maybeResumePendingCanonTurn()
        }

    fun sendMessage(text: String) {
        val campaign = selectedCampaign.value ?: return
        if (text.isBlank() || _isGenerating.value || _pendingCanonTurn.value != null) return

        viewModelScope.launch {
            _isGenerating.value = true
            _lastError.value = null

            try {
                repository.addMessage(campaign.id, "user", text)
                val pendingBeforeTurn = repository.pendingProposalIds(campaign.id)

                // Phase 1: deterministic canon detection.
                repository.proposeExplicitCharacterMovementCommand(campaign.id, text)
                repository.proposeExplicitQuestCommand(campaign.id, text)

                val provider: AiProvider =
                    if (_providerSettings.value.enabled) OpenAiCompatibleProvider { settingsStore.load() }
                    else ChronicleDemoProvider()

                // Phase 1b: AI canon analysis happens BEFORE storyteller generation.
                if (_providerSettings.value.enabled) {
                    scanForProposals(
                        campaign = campaign,
                        context = repository.buildAutomationContextSnapshot(campaign),
                        userText = text,
                        assistantReply = "",
                        provider = provider
                    )
                }

                val newPending = repository.pendingProposalIds(campaign.id) - pendingBeforeTurn
                if (newPending.isNotEmpty()) {
                    _pendingTurnProposalIds = newPending
                    _pendingCanonTurn.value = text
                    _notice.value = "${newPending.size} canon change${if (newPending.size == 1) "" else "s"} need Review before the story continues."
                    return@launch
                }

                generateStoryReply(campaign, text, provider)
            } catch (t: Throwable) {
                _lastError.value = t.message ?: "Unknown AI provider error."
            } finally {
                _isGenerating.value = false
            }
        }
    }

    private suspend fun generateStoryReply(
        campaign: CampaignEntity,
        userText: String,
        provider: AiProvider
    ) {
        val freshCampaign = repository.campaignById(campaign.id) ?: campaign
        val context = repository.buildCanonicalContextSnapshot(freshCampaign)
        val history = repository.recentMessages(campaign.id, 40)
            .map { ProviderMessage(it.role, it.content) }

        val system = """
            You are Chronicle's campaign storyteller and GM.

            CANON-FIRST AUTHORITY
            - Chronicle Review has already resolved any persistent changes that required player approval for this turn.
            - The fresh database snapshot supplied in context is the ONLY source of canonical truth.
            - Narrate from accepted canon. Never resurrect rejected, superseded, or unapproved proposed facts.
            - Review is a separate app UI. NEVER ask the player to approve, confirm, accept, authorize, or reject anything in Chat.
            - Do not create a second character sheet, quest tracker, world-state ledger, or approval workflow inside Chat.

            CHARACTER PRESENCE
            - Characters may occupy different locations simultaneously.
            - Respect each character's latest canonical "Currently at LOCATION." note.
            - Never teleport companions or NPCs merely because the campaign current location changed.

            WORLD / QUEST AUTHORITY
            - Approved World, Quest, Timeline, Memory, Campaign, and Character records in context govern continuity.
            - Use exact canonical location and quest names where possible.
            - If a fact is absent from approved canon, do not pretend it was accepted.

            STORY RULES
            Preserve established canon, causal continuity, character autonomy, tone, and consequences.
            Never import facts from another campaign.
            Continue naturally from the player's latest message using the now-resolved canonical state.
        """.trimIndent()

        val reply = provider.generate(
            ProviderRequest(
                systemPrompt = system,
                memoryContext = context,
                messages = history
            )
        )
        repository.addMessage(campaign.id, "assistant", reply)
    }

    private fun maybeResumePendingCanonTurn() {
        val userText = _pendingCanonTurn.value ?: return
        val campaign = selectedCampaign.value ?: return
        viewModelScope.launch {
            // Approval/rejection writes happen immediately, but group operations may resolve several
            // rows in sequence. Briefly let the transaction settle before deciding to resume.
            delay(120)
            val unresolvedNew = repository.pendingProposalIds(campaign.id).intersect(_pendingTurnProposalIds)
            if (unresolvedNew.isNotEmpty()) return@launch

            _pendingCanonTurn.value = null
            _isGenerating.value = true
            try {
                val provider: AiProvider =
                    if (_providerSettings.value.enabled) OpenAiCompatibleProvider { settingsStore.load() }
                    else ChronicleDemoProvider()
                generateStoryReply(campaign, userText, provider)
            } catch (t: Throwable) {
                _lastError.value = t.message ?: "Could not resume the pending story turn."
            } finally {
                _isGenerating.value = false
                _pendingTurnProposalIds = emptySet()
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
            // Manual Scan must use the same deterministic safety paths as normal chat.
            // This makes Scan a genuine recovery tool instead of an AI-only retry.
            repository.proposeExplicitCharacterMovementCommand(campaign.id, lastUser.content)
            repository.proposeExplicitQuestCommand(campaign.id, lastUser.content)

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
                - CANON-FIRST MODE: the storyteller reply may be absent. Detect all durable changes explicitly established by the player message before narration.

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

                MAP CONNECTION RULES
                - When the exchange explicitly establishes a direct route between two approved locations, preserve that relationship in location/world proposals.
                - Never invent distance, direction, danger, or accessibility.
                - Existing parentLocation hierarchy remains a valid visual connection.
                - Travel between two places does not automatically mean one is the parent of the other.

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

                CHARACTER PRESENCE RULES
                - Track independently where named characters currently are when the latest exchange explicitly establishes it.
                - Character location is NOT automatically identical to campaign currentLocation; split parties and NPCs may remain elsewhere.
                - For an existing character whose location changes, propose character_update and append a concise notes statement using the exact form "Currently at LOCATION_NAME.".
                - When a character explicitly stays behind, preserve that character at the old location instead of moving everyone with the party.
                - Do not infer an NPC's current location merely because they are associated with a place historically.
                - When multiple named characters travel together, evaluate each character independently.
                - This canonical note powers World Explorer character badges in v0.10.1.

                WORLD EXPLORER RULES
                - The approved Location records power the visual World Explorer map.
                - When the party physically arrives at a named location, propose location_upsert with discoveryState="Visited".
                - When a place is learned about but not reached, use discoveryState="Heard About".
                - When a place becomes known/discovered but is not clearly visited, use discoveryState="Discovered".
                - Preserve parentLocation whenever the source establishes that one place is inside/part of another place or region.
                - If the party's actual current location changes, ALSO propose campaign_update with currentLocation set to the exact approved location name.
                - Do not mark a location Visited merely because an NPC mentions it.

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
                            "LATEST USER MESSAGE:\n$userText\n\nSTORYTELLER REPLY:\n${assistantReply.ifBlank { "(not generated yet; detect proposals from the player message and fresh canonical context only)" }}"
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
