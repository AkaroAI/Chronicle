package com.akaroai.chronicle.provider

import org.json.JSONArray
import org.json.JSONObject

data class ParsedProposal(
    val summary: String,
    val targetType: String,
    val targetId: Long?,
    val proposedChanges: String,
    val reason: String
)

object ProposalParser {
    fun parse(raw: String): List<ParsedProposal> {
        val cleaned = raw
            .replace("```json", "")
            .replace("```", "")
            .trim()

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

            if (
                summary.isNotBlank() &&
                targetType in setOf("memory_new", "character_update", "campaign_update") &&
                changes.length() > 0
            ) {
                result += ParsedProposal(
                    summary = summary,
                    targetType = targetType,
                    targetId = targetId,
                    proposedChanges = changes.toString(),
                    reason = reason
                )
            }
        }

        return result.take(5)
    }

    fun prettyChanges(raw: String): String {
        return try {
            val obj = JSONObject(raw)
            val lines = mutableListOf<String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
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

    private fun humanize(value: String): String =
        value.replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace("_", " ")
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
