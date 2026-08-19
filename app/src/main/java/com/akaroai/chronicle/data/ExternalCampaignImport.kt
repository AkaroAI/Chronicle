package com.akaroai.chronicle.data

import com.akaroai.chronicle.model.CharacterEntity
import org.json.JSONArray
import org.json.JSONObject

data class ImportedCharacterDraft(
    val name: String,
    val castTier: String,
    val species: String = "",
    val age: String = "",
    val pronouns: String = "",
    val appearance: String = "",
    val personality: String = "",
    val backstory: String = "",
    val abilities: String = "",
    val equipment: String = "",
    val relationship: String = "",
    val affiliations: String = "",
    val goals: String = "",
    val fears: String = "",
    val secrets: String = "",
    val injuries: String = "",
    val notes: String = "",
    val status: String = "Active",
    val confidence: String = "Needs review"
)

data class ImportedMemoryDraft(
    val category: String,
    val title: String,
    val content: String,
    val confidence: String = "Needs review"
)


data class ImportedLocationDraft(
    val name: String,
    val region: String = "",
    val parentLocation: String = "",
    val description: String = "",
    val discoveryState: String = "Discovered",
    val status: String = "Active",
    val notes: String = "",
    val confidence: String = "Needs review"
)

data class ImportedFactionDraft(
    val name: String,
    val description: String = "",
    val alignment: String = "",
    val relationshipToParty: String = "Unknown",
    val status: String = "Active",
    val goals: String = "",
    val notes: String = "",
    val confidence: String = "Needs review"
)

data class ImportedQuestDraft(
    val title: String,
    val summary: String = "",
    val status: String = "Active",
    val objective: String = "",
    val relatedLocation: String = "",
    val relatedFaction: String = "",
    val importance: String = "Normal",
    val notes: String = "",
    val confidence: String = "Needs review"
)

data class ImportedTimelineDraft(
    val title: String,
    val summary: String,
    val eventType: String = "Event",
    val location: String = "",
    val involvedCharacters: String = "",
    val storyArc: String = "",
    val importance: String = "Normal",
    val confidence: String = "Needs review"
)

data class ImportedMessageDraft(val role: String, val content: String)

data class ExternalImportDraft(
    val campaignName: String,
    val description: String,
    val setting: String,
    val genreTone: String,
    val currentLocation: String,
    val currentObjective: String,
    val characters: List<ImportedCharacterDraft>,
    val memories: List<ImportedMemoryDraft>,
    val locations: List<ImportedLocationDraft> = emptyList(),
    val factions: List<ImportedFactionDraft> = emptyList(),
    val quests: List<ImportedQuestDraft> = emptyList(),
    val timelineEvents: List<ImportedTimelineDraft> = emptyList(),
    val messages: List<ImportedMessageDraft>,
    val sourceText: String
)

object ExternalCampaignImport {
    fun parseAnalysis(raw: String, sourceText: String): ExternalImportDraft {
        val clean = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        val o = JSONObject(clean)
        return ExternalImportDraft(
            campaignName = o.optString("campaignName", "Imported Campaign").ifBlank { "Imported Campaign" },
            description = o.optString("description"),
            setting = o.optString("setting"),
            genreTone = o.optString("genreTone"),
            currentLocation = o.optString("currentLocation"),
            currentObjective = o.optString("currentObjective"),
            characters = o.optJSONArray("characters").toCharacterDrafts(),
            memories = o.optJSONArray("memories").toMemoryDrafts(),
            locations = o.optJSONArray("locations").toLocationDrafts(),
            factions = o.optJSONArray("factions").toFactionDrafts(),
            quests = o.optJSONArray("quests").toQuestDrafts(),
            timelineEvents = o.optJSONArray("timelineEvents").toTimelineDrafts(),
            messages = parseTranscript(sourceText),
            sourceText = sourceText
        )
    }

    private fun JSONArray?.toCharacterDrafts(): List<ImportedCharacterDraft> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val o = optJSONObject(i) ?: continue
                val tier = o.optString("castTier", "Supporting")
                    .takeIf { it in setOf("Main","Secondary","Supporting","Background") }
                    ?: "Supporting"
                add(ImportedCharacterDraft(
                    name=o.optString("name").trim(), castTier=tier,
                    species=o.optString("species"), age=o.optString("age"),
                    pronouns=o.optString("pronouns"), appearance=o.optString("appearance"),
                    personality=o.optString("personality"), backstory=o.optString("backstory"),
                    abilities=o.optString("abilities"), equipment=o.optString("equipment"),
                    relationship=o.optString("relationship"), affiliations=o.optString("affiliations"),
                    goals=o.optString("goals"), fears=o.optString("fears"),
                    secrets=o.optString("secrets"), injuries=o.optString("injuries"),
                    notes=o.optString("notes"), status=o.optString("status","Active"),
                    confidence=normalizeConfidence(o.optString("confidence"))
                ))
            }
        }.filter { it.name.isNotBlank() }
    }

    private fun JSONArray?.toMemoryDrafts(): List<ImportedMemoryDraft> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val o = optJSONObject(i) ?: continue
                val title = o.optString("title").trim()
                val content = o.optString("content").trim()
                if (title.isNotBlank() && content.isNotBlank()) add(
                    ImportedMemoryDraft(
                        o.optString("category","Canon").ifBlank { "Canon" },
                        title, content, normalizeConfidence(o.optString("confidence"))
                    )
                )
            }
        }
    }


    private fun JSONArray?.toLocationDrafts(): List<ImportedLocationDraft> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val o = optJSONObject(i) ?: continue
                val name = o.optString("name").trim()
                if (name.isNotBlank()) add(
                    ImportedLocationDraft(
                        name = name,
                        region = o.optString("region"),
                        parentLocation = o.optString("parentLocation"),
                        description = o.optString("description"),
                        discoveryState = o.optString("discoveryState","Discovered"),
                        status = o.optString("status","Active"),
                        notes = o.optString("notes"),
                        confidence = normalizeConfidence(o.optString("confidence"))
                    )
                )
            }
        }.distinctBy { it.name.lowercase() }
    }

    private fun JSONArray?.toFactionDrafts(): List<ImportedFactionDraft> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val o = optJSONObject(i) ?: continue
                val name = o.optString("name").trim()
                if (name.isNotBlank()) add(
                    ImportedFactionDraft(
                        name = name,
                        description = o.optString("description"),
                        alignment = o.optString("alignment"),
                        relationshipToParty = o.optString("relationshipToParty","Unknown"),
                        status = o.optString("status","Active"),
                        goals = o.optString("goals"),
                        notes = o.optString("notes"),
                        confidence = normalizeConfidence(o.optString("confidence"))
                    )
                )
            }
        }.distinctBy { it.name.lowercase() }
    }

    private fun JSONArray?.toQuestDrafts(): List<ImportedQuestDraft> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val o = optJSONObject(i) ?: continue
                val title = o.optString("title").trim()
                if (title.isNotBlank()) add(
                    ImportedQuestDraft(
                        title = title,
                        summary = o.optString("summary"),
                        status = o.optString("status","Active"),
                        objective = o.optString("objective"),
                        relatedLocation = o.optString("relatedLocation"),
                        relatedFaction = o.optString("relatedFaction"),
                        importance = o.optString("importance","Normal"),
                        notes = o.optString("notes"),
                        confidence = normalizeConfidence(o.optString("confidence"))
                    )
                )
            }
        }.distinctBy { it.title.lowercase() }
    }

    private fun JSONArray?.toTimelineDrafts(): List<ImportedTimelineDraft> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val o = optJSONObject(i) ?: continue
                val title = o.optString("title").trim()
                val summary = o.optString("summary").trim()
                if (title.isNotBlank() && summary.isNotBlank()) add(
                    ImportedTimelineDraft(
                        title = title,
                        summary = summary,
                        eventType = o.optString("eventType","Event"),
                        location = o.optString("location"),
                        involvedCharacters = o.optString("involvedCharacters"),
                        storyArc = o.optString("storyArc"),
                        importance = o.optString("importance","Normal"),
                        confidence = normalizeConfidence(o.optString("confidence"))
                    )
                )
            }
        }.distinctBy { "${it.title.lowercase()}|${it.summary.lowercase()}" }
    }

    private fun normalizeConfidence(v: String): String = when (v.lowercase()) {
        "high", "high confidence" -> "High confidence"
        "ambiguous" -> "Ambiguous"
        else -> "Needs review"
    }

    // Deterministic transcript preservation when common speaker prefixes exist.
    fun parseTranscript(text: String): List<ImportedMessageDraft> {
        val speaker = Regex("""(?im)^(User|Human|Player|Assistant|Chronicle|GM|Game Master)\s*:\s*""")
        val matches = speaker.findAll(text).toList()
        if (matches.isEmpty()) return emptyList()
        return matches.mapIndexedNotNull { index, m ->
            val start = m.range.last + 1
            val end = matches.getOrNull(index + 1)?.range?.first ?: text.length
            val body = text.substring(start, end).trim()
            if (body.isBlank()) null else ImportedMessageDraft(
                role = when (m.groupValues[1].lowercase()) {
                    "user","human","player" -> "user"
                    else -> "assistant"
                },
                content = body
            )
        }
    }
}
