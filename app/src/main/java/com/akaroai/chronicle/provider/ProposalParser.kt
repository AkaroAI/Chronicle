package com.akaroai.chronicle.provider

import org.json.JSONArray
import org.json.JSONObject

data class ParsedProposal(
    val summary: String,
    val targetType: String,
    val targetId: Long?,
    val proposedChanges: String,
    val reason: String,
    val priority: String,
    val groupType: String,
    val groupLabel: String,
    val changeMode: String,
    val evidenceType: String
)

object ProposalParser {
    private val priorities = setOf("Critical", "High", "Normal", "Low")
    private val groups = setOf("Characters", "World", "Events", "Lore", "Relationships", "Quests", "Other")
    private val changeModes = setOf("Append", "Replace", "Clear")
    private val evidenceTypes = setOf("Player Confirmed", "Story Event", "Assistant Only", "Unverified")

    fun parse(raw: String): List<ParsedProposal> {
        val cleaned = raw.replace("```json", "").replace("```", "").trim()
        val start = cleaned.indexOf('[')
        val end = cleaned.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()

        val array = JSONArray(cleaned.substring(start, end + 1))
        val result = mutableListOf<ParsedProposal>()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val summary = item.optString("summary").trim()
            val targetType = item.optString("targetType").trim()
            val reason = item.optString("reason").trim()
            val changes = item.optJSONObject("changes") ?: JSONObject()
            val targetId = if (item.has("targetId") && !item.isNull("targetId")) {
                item.optLong("targetId").takeIf { it > 0 }
            } else null

            val priority = item.optString("priority", "Normal")
                .takeIf { it in priorities } ?: "Normal"
            val groupType = item.optString("groupType", defaultGroup(targetType))
                .takeIf { it in groups } ?: defaultGroup(targetType)
            val groupLabel = item.optString("groupLabel").trim()
            val changeMode = item.optString("changeMode", defaultChangeMode(targetType))
                .takeIf { it in changeModes } ?: defaultChangeMode(targetType)
            val evidenceType = item.optString("evidenceType", "Unverified")
                .takeIf { it in evidenceTypes } ?: "Unverified"

            if (
                summary.isNotBlank() &&
                targetType in setOf(
                    "memory_new",
                    "character_new",
                    "character_update",
                    "campaign_update",
                    "cast_tier_update",
                    "location_upsert",
                    "faction_upsert",
                    "quest_upsert",
                    "timeline_event_new"
                ) &&
                changes.length() > 0
            ) {
                result += ParsedProposal(
                    summary = summary,
                    targetType = targetType,
                    targetId = targetId,
                    proposedChanges = changes.toString(),
                    reason = reason,
                    priority = priority,
                    groupType = groupType,
                    groupLabel = groupLabel,
                    changeMode = changeMode,
                    evidenceType = evidenceType
                )
            }
        }
        return result.take(12)
    }

    fun prettyChanges(raw: String): String {
        return try {
            val obj = JSONObject(raw)
            val lines = mutableListOf<String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key.startsWith("__")) continue
                val value = obj.opt(key)
                if (key == "fields" && value is JSONObject) {
                    val fieldKeys = value.keys()
                    while (fieldKeys.hasNext()) {
                        val field = fieldKeys.next()
                        lines += "${humanize(field)} → ${value.optString(field)}"
                    }
                } else {
                    lines += "${humanize(key)} → ${value?.toString().orEmpty()}"
                }
            }
            lines.joinToString("\n")
        } catch (_: Throwable) {
            raw
        }
    }

    fun changedFieldNames(raw: String): Set<String> {
        return try {
            val obj = JSONObject(raw)
            val fields = obj.optJSONObject("fields") ?: obj
            buildSet {
                val keys = fields.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (!key.startsWith("__")) add(key)
                }
            }
        } catch (_: Throwable) {
            emptySet()
        }
    }

    private fun defaultGroup(targetType: String): String =
        when (targetType) {
            "character_new", "character_update", "cast_tier_update" -> "Characters"
            "campaign_update", "location_upsert", "faction_upsert" -> "World"
            "quest_upsert" -> "Quests"
            "timeline_event_new" -> "Events"
            "memory_new" -> "Lore"
            else -> "Other"
        }

    private fun defaultChangeMode(targetType: String): String =
        when (targetType) {
            "character_update" -> "Append"
            else -> "Replace"
        }

    private fun humanize(value: String): String =
        value.replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace("_", " ")
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
