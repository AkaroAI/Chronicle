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
